/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.model.base.headers.DittoHeaderDefinition.CORRELATION_ID;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersSizeChecker;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.MessageMappingFailedException;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.eclipse.ditto.model.connectivity.PayloadMappingDefinition;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.base.config.limits.LimitsConfig;
import org.eclipse.ditto.services.connectivity.mapping.DefaultMessageMapperFactory;
import org.eclipse.ditto.services.connectivity.mapping.DittoMessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapper;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperFactory;
import org.eclipse.ditto.services.connectivity.mapping.MessageMapperRegistry;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectivityConfig;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.models.connectivity.MappedInboundExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.signals.base.Signal;

import akka.actor.ActorSystem;
import akka.event.DiagnosticLoggingAdapter;

/**
 * Processes incoming {@link ExternalMessage}s to {@link Signal}s and {@link Signal}s back to {@link ExternalMessage}s.
 * Encapsulates the message processing logic from the message mapping processor actor.
 */
public final class MessageMappingProcessor {

    private final ConnectionId connectionId;
    private final MessageMapperRegistry registry;
    private final DiagnosticLoggingAdapter log;
    private final ProtocolAdapter protocolAdapter;
    private final DittoHeadersSizeChecker dittoHeadersSizeChecker;

    private MessageMappingProcessor(final ConnectionId connectionId,
            final MessageMapperRegistry registry,
            final DiagnosticLoggingAdapter log,
            final ProtocolAdapter protocolAdapter,
            final DittoHeadersSizeChecker dittoHeadersSizeChecker) {

        this.connectionId = connectionId;
        this.registry = registry;
        this.log = log;
        this.protocolAdapter = protocolAdapter;
        this.dittoHeadersSizeChecker = dittoHeadersSizeChecker;
    }

    /**
     * Initializes a new command processor with mappers defined in mapping mappingContext.
     * The dynamic access is needed to instantiate message mappers for an actor system.
     *
     * @param connectionId the connection that the processor works for.
     * @param mappingDefinition the configured mappings used by this processor
     * @param actorSystem the dynamic access used for message mapper instantiation.
     * @param connectivityConfig the configuration settings of the Connectivity service.
     * @param protocolAdapterProvider provides the ProtocolAdapter to be used.
     * @param log the log adapter.
     * @return the processor instance.
     * @throws org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException if the configuration of
     * one of the {@code mappingContext} is invalid.
     * @throws org.eclipse.ditto.model.connectivity.MessageMapperConfigurationFailedException if the configuration of
     * one of the {@code mappingContext} failed for a mapper specific reason.
     */
    public static MessageMappingProcessor of(final ConnectionId connectionId,
            final PayloadMappingDefinition mappingDefinition,
            final ActorSystem actorSystem,
            final ConnectivityConfig connectivityConfig,
            final ProtocolAdapterProvider protocolAdapterProvider,
            final DiagnosticLoggingAdapter log) {

        final MessageMapperFactory messageMapperFactory =
                DefaultMessageMapperFactory.of(connectionId, actorSystem, connectivityConfig.getMappingConfig(), log);
        final MessageMapperRegistry registry =
                messageMapperFactory.registryOf(DittoMessageMapper.CONTEXT, mappingDefinition);

        final LimitsConfig limitsConfig = connectivityConfig.getLimitsConfig();
        final DittoHeadersSizeChecker dittoHeadersSizeChecker =
                DittoHeadersSizeChecker.of(limitsConfig.getHeadersMaxSize(), limitsConfig.getAuthSubjectsMaxCount());

        return new MessageMappingProcessor(connectionId, registry, log,
                protocolAdapterProvider.getProtocolAdapter(null), dittoHeadersSizeChecker);
    }

    /**
     * @return the message mapper registry to use for mapping messages.
     */
    MessageMapperRegistry getRegistry() {
        return registry;
    }

    /**
     * Processes an {@link ExternalMessage} which may result in 0..n messages/errors.
     *
     * @param message the inbound {@link ExternalMessage} to be processed
     * @param resultHandler handles the 0..n results of the mapping(s)
     * @return combined results of all message mappers.
     */
    <R> R process(final ExternalMessage message,
            final MappingResultHandler<MappedInboundExternalMessage, R> resultHandler) {
        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log,
                message.getHeaders().get(CORRELATION_ID.getKey()), connectionId);
        final List<MessageMapper> mappers = getMappers(message);
        log.debug("Mappers resolved for message: {}", mappers);
        R result = resultHandler.emptyResult();
        for (final MessageMapper mapper : mappers) {
            final MappingTimer mappingTimer = MappingTimer.inbound(connectionId);
            final R mappingResult =
                    mappingTimer.overall(() -> convertInboundMessage(mapper, message, mappingTimer, resultHandler));
            result = resultHandler.combineResults(result, mappingResult);
        }
        return result;
    }

    /**
     * Processes an {@link OutboundSignal} to 0..n {@link OutboundSignal.Mapped} signals and passes them to the given
     * {@link MappingResultHandler}.
     *
     * @param outboundSignal the outboundSignal to be processed
     * @param resultHandler handles the 0..n results of the mapping(s)
     */
    void process(final OutboundSignal outboundSignal,
            final MappingResultHandler<OutboundSignal.Mapped, Void> resultHandler) {
        final List<OutboundSignal.Mappable> mappableSignals;
        if (outboundSignal.getTargets().isEmpty()) {
            // responses/errors do not have a target assigned, read mapper used for inbound message from internal header
            final PayloadMapping payloadMapping = outboundSignal.getSource()
                    .getDittoHeaders()
                    .getInboundPayloadMapper()
                    .map(ConnectivityModelFactory::newPayloadMapping)
                    .orElseGet(ConnectivityModelFactory::emptyPayloadMapping); // fallback to default payload mapping
            final OutboundSignal.Mappable mappableSignal =
                    OutboundSignalFactory.newMappableOutboundSignal(outboundSignal.getSource(),
                            outboundSignal.getTargets(), payloadMapping);
            mappableSignals = Collections.singletonList(mappableSignal);
        } else {
            // group targets with exact same list of mappers together to avoid redundant mappings
            mappableSignals = outboundSignal.getTargets()
                    .stream()
                    .collect(Collectors.groupingBy(Target::getPayloadMapping, LinkedHashMap::new, Collectors.toList()))
                    .entrySet()
                    .stream()
                    .map(e -> OutboundSignalFactory.newMappableOutboundSignal(outboundSignal.getSource(), e.getValue(),
                            e.getKey()))
                    .collect(Collectors.toList());
        }
        processMappableSignals(outboundSignal, mappableSignals, resultHandler);
    }

    private void processMappableSignals(final OutboundSignal outboundSignal,
            final List<OutboundSignal.Mappable> mappableSignals,
            final MappingResultHandler<OutboundSignal.Mapped, Void> resultHandler) {

        final MappingTimer timer = MappingTimer.outbound(connectionId);
        final Adaptable adaptableWithoutExtra =
                timer.protocol(() -> protocolAdapter.toAdaptable(outboundSignal.getSource()));
        final Adaptable adaptable = outboundSignal.getExtra()
                .map(extra -> ProtocolFactory.setExtra(adaptableWithoutExtra, extra))
                .orElse(adaptableWithoutExtra);
        enhanceLogFromAdaptable(adaptable);

        for (final OutboundSignal.Mappable mappableSignal : mappableSignals) {
            final List<MessageMapper> mappers = getMappers(mappableSignal.getPayloadMapping());
            final Optional<String> correlationId = mappableSignal.getSource().getDittoHeaders().getCorrelationId();
            final List<Target> targets = mappableSignal.getTargets();
            log.debug("Resolved mappers for message {} to targets {}: {}", correlationId, targets, mappers);
            // convert messages in the order of payload mapping and forward to result handler
            mappers.forEach(mapper -> convertOutboundMessage(mappableSignal, adaptable, mapper, timer, resultHandler));
        }
    }

    /**
     * Retrieve the header translator of the protocol adapter.
     *
     * @return the header translator.
     */
    HeaderTranslator getHeaderTranslator() {
        return protocolAdapter.headerTranslator();
    }

    private <R> R convertInboundMessage(final MessageMapper mapper,
            final ExternalMessage message,
            final MappingTimer timer,
            final MappingResultHandler<MappedInboundExternalMessage, R> handler) {

        checkNotNull(message, "message");
        R result = handler.emptyResult();
        try {
            final boolean shouldMapMessage = message.findContentType()
                    .map(filterByContentTypeBlacklist(mapper))
                    .orElse(true); // if no content-type was present, map the message!

            if (shouldMapMessage) {
                log.debug("Mapping message using mapper {}.", mapper.getId());
                final List<Adaptable> adaptables = timer.payload(mapper.getId(), () -> mapper.map(message));
                if (isNullOrEmpty(adaptables)) {
                    return handler.onMessageDropped();
                } else {
                    for (final Adaptable adaptable : adaptables) {
                        enhanceLogFromAdaptable(adaptable);
                        final Signal<?> signal = timer.protocol(() -> protocolAdapter.fromAdaptable(adaptable));
                        dittoHeadersSizeChecker.check(signal.getDittoHeaders());
                        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
                        final DittoHeaders headersWithMapper =
                                dittoHeaders.toBuilder().inboundPayloadMapper(mapper.getId()).build();
                        final Signal<?> signalWithMapperHeader = signal.setDittoHeaders(headersWithMapper);
                        final MappedInboundExternalMessage mappedMessage =
                                MappedInboundExternalMessage.of(message, adaptable.getTopicPath(),
                                        signalWithMapperHeader);
                        result = handler.combineResults(result, handler.onMessageMapped(mappedMessage));
                    }
                }
            } else {
                result = handler.onMessageDropped();
                log.debug("Not mapping message with mapper <{}> as content-type <{}> was blacklisted.",
                        mapper.getId(), message.findContentType());
            }
        } catch (final DittoRuntimeException e) {
            // combining error result with any previously successfully mapped result
            result = handler.combineResults(result, handler.onException(e));
        } catch (final Exception e) {
            final MessageMappingFailedException mappingFailedException = buildMappingFailedException("inbound",
                    message.findContentType().orElse(""), mapper.getId(), DittoHeaders.of(message.getHeaders()), e);
            // combining error result with any previously successfully mapped result
            result = handler.combineResults(result, handler.onException(mappingFailedException));
        }
        return result;
    }

    private static Function<String, Boolean> filterByContentTypeBlacklist(final MessageMapper mapper) {
        return contentType -> !mapper.getContentTypeBlacklist().contains(contentType);
    }

    private void convertOutboundMessage(
            final OutboundSignal.Mappable outboundSignal,
            final Adaptable adaptable, final MessageMapper mapper,
            final MappingTimer timer, final MappingResultHandler<OutboundSignal.Mapped, Void> resultHandler) {
        try {

            log.debug("Applying mapper {} to message {}", mapper.getId(),
                    adaptable.getDittoHeaders().getCorrelationId());

            final List<OutboundSignal.Mapped> messages = timer.payload(mapper.getId(),
                    () -> toStream(mapper.map(adaptable))
                            .map(em -> {
                                final ExternalMessage externalMessage =
                                        ExternalMessageFactory.newExternalMessageBuilder(em)
                                                .withTopicPath(adaptable.getTopicPath())
                                                // TODO check if same as signal.getDittoHeaders()
                                                .withInternalHeaders(adaptable.getDittoHeaders())
                                                .build();
                                return OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable,
                                        externalMessage);
                            })
                            .collect(Collectors.toList()));

            log.debug("Mapping '{}' produced {} messages.", mapper.getId(), messages.size());

            if (messages.isEmpty()) {
                resultHandler.onMessageDropped();
            } else {
                messages.forEach(resultHandler::onMessageMapped);
            }
        } catch (final DittoRuntimeException e) {
            resultHandler.onException(e);
        } catch (final Exception e) {
            final Optional<DittoHeaders> headers = adaptable.getHeaders();
            final String contentType = headers.map(h -> h.get(ExternalMessage.CONTENT_TYPE_HEADER)).orElse("");
            final MessageMappingFailedException mappingFailedException =
                    buildMappingFailedException("outbound", contentType, mapper.getId(),
                            headers.orElseGet(DittoHeaders::empty), e);
            resultHandler.onException(mappingFailedException);
        }
    }

    private <T> Stream<T> toStream(@Nullable final List<T> messages) {
        return messages == null ? Stream.empty() : messages.stream();
    }

    private static boolean isNullOrEmpty(@Nullable final List<?> messages) {
        return messages == null || messages.isEmpty();
    }

    private List<MessageMapper> getMappers(final ExternalMessage message) {
        final List<MessageMapper> mappings =
                message.getPayloadMapping().map(registry::getMappers).orElseGet(Collections::emptyList);
        if (mappings.isEmpty()) {
            log.debug("Falling back to Default MessageMapper for mapping ExternalMessage " +
                    "as no MessageMapper was present: {}", message);
            return Collections.singletonList(registry.getDefaultMapper());
        } else {
            return mappings;
        }
    }

    private List<MessageMapper> getMappers(final PayloadMapping payloadMapping) {
        final List<MessageMapper> mappers = registry.getMappers(payloadMapping);
        if (mappers.isEmpty()) {
            log.debug("Falling back to Default MessageMapper for mapping as no MessageMapper was present.");
            return Collections.singletonList(registry.getDefaultMapper());
        } else {
            return mappers;
        }
    }

    private void enhanceLogFromAdaptable(final Adaptable adaptable) {
        ConnectionLogUtil.enhanceLogWithCorrelationIdAndConnectionId(log, adaptable, connectionId);
    }

    private MessageMappingFailedException buildMappingFailedException(final String direction,
            final String contentType, final String mapperId, final DittoHeaders dittoHeaders, final Exception e) {
        final String description =
                String.format("Could not map %s message with mapper '%s' due to unknown problem: %s %s",
                        direction, mapperId, e.getClass().getSimpleName(), e.getMessage());
        return MessageMappingFailedException.newBuilder(contentType)
                .description(description)
                .dittoHeaders(dittoHeaders)
                .cause(e)
                .build();
    }
}
