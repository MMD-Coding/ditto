/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.gateway.endpoints.utils;

import java.util.Arrays;

import org.eclipse.ditto.services.gateway.streaming.StreamingConfig;
import org.eclipse.ditto.services.models.things.ThingEnrichingFacade;
import org.eclipse.ditto.services.utils.akka.AkkaClassLoader;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;

/**
 * Provider of {@link org.eclipse.ditto.services.models.things.ThingEnrichingFacade} to be loaded by reflection.
 * Implementations MUST have a public constructor taking the following parameters as arguments:
 * <ul>
 * <li>ActorRef commandHandler: recipient of retrieve-thing commands,</li>
 * <li>Config config: configuration for the facade provider.</li>
 * </ul>
 */
public interface ThingEnrichingFacadeProvider {

    /**
     * Create a {@link org.eclipse.ditto.services.models.things.ThingEnrichingFacade} from the HTTP request that
     * created the websocket or SSE stream that requires it.
     *
     * @param request the HTTP request.
     * @return the thing-enriching facade.
     */
    ThingEnrichingFacade createFacade(HttpRequest request);

    /**
     * Load a {@code ThingEnrichingFacadeProvider} dynamically according to the streaming configuration.
     *
     * @param actorSystem The actor system in which to load the facade provider class.
     * @param commandHandler The recipient of retrieve-thing commands.
     * @param streamingConfig The streaming configuration containing the fully qualified name of the facade provider.
     * @return The configured facade provider.
     */
    static ThingEnrichingFacadeProvider load(final ActorSystem actorSystem,
            final ActorRef commandHandler, final StreamingConfig streamingConfig) {

        return AkkaClassLoader.instantiate(actorSystem, ThingEnrichingFacadeProvider.class,
                streamingConfig.getThingEnrichmentProvider(),
                Arrays.asList(ActorRef.class, Config.class),
                Arrays.asList(commandHandler, streamingConfig.getThingEnrichmentConfig())
        );
    }
}
