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
package org.eclipse.ditto.signals.commands.messages;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.messages.Message;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * Command to send a ClaimMessage <em>TO</em> a Thing.
 *
 * @param <T> the type of the message's payload.
 */
@JsonParsableCommand(typePrefix = SendClaimMessage.TYPE_PREFIX, name = SendClaimMessage.NAME)
public final class SendClaimMessage<T> extends AbstractMessageCommand<T, SendClaimMessage> {

    /**
     * The name of the {@code Message} wrapped by this {@code MessageCommand}.
     */
    public static final String NAME = "claimMessage";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private SendClaimMessage(final ThingId thingId, final Message<T> message, final DittoHeaders dittoHeaders) {
        super(TYPE, thingId, message, dittoHeaders);
    }

    @Override
    public SendClaimMessage setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getThingEntityId(), getMessage(), dittoHeaders);
    }

    /**
     * Creates a new instance of {@link SendClaimMessage}.
     *
     * @param thingId the ID of the Thing to send the message to
     * @param message the message to send to the Thing
     * @param dittoHeaders the DittoHeaders of this message.
     * @param <T> the type of the message's payload.
     * @return new instance of {@link SendClaimMessage}.
     * @throws NullPointerException if any argument is {@code null}.
     * @deprecated Thing ID is now typed. Use
     * {@link #of(org.eclipse.ditto.model.things.ThingId, org.eclipse.ditto.model.messages.Message, org.eclipse.ditto.model.base.headers.DittoHeaders)}
     * instead.
     */
    @Deprecated
    public static <T> SendClaimMessage<T> of(final String thingId, final Message<T> message,
            final DittoHeaders dittoHeaders) {
        return of(ThingId.of(thingId), message, dittoHeaders);
    }

    /**
     * Creates a new instance of {@link SendClaimMessage}.
     *
     * @param thingId the ID of the Thing to send the message to
     * @param message the message to send to the Thing
     * @param dittoHeaders the DittoHeaders of this message.
     * @param <T> the type of the message's payload.
     * @return new instance of {@link SendClaimMessage}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static <T> SendClaimMessage<T> of(final ThingId thingId, final Message<T> message,
            final DittoHeaders dittoHeaders) {
        return new SendClaimMessage<>(thingId, message, dittoHeaders);
    }

    /**
     * Creates a new {@code SendClaimMessage} from a JSON string.
     *
     * @param jsonString the JSON string of which the SendClaimMessage is to be created.
     * @param dittoHeaders the headers.
     * @param <T> the type of the message's payload
     * @return the command.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected format.
     */
    public static <T> SendClaimMessage<T> fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code SendClaimMessage} from a JSON object.
     *
     * @param jsonObject the JSON object of which the SendClaimMessage is to be created.
     * @param dittoHeaders the headers.
     * @param <T> the type of the message's payload
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static <T> SendClaimMessage<T> fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<SendClaimMessage<T>>(TYPE, jsonObject).deserialize(() -> {
            final String extractedThingId = jsonObject.getValueOrThrow(MessageCommand.JsonFields.JSON_THING_ID);
            final ThingId thingId = ThingId.of(extractedThingId);
            final Message<T> message = deserializeMessageFromJson(jsonObject);

            return of(thingId, message, dittoHeaders);
        });
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return (other instanceof SendClaimMessage);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + "]";
    }
}
