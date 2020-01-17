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
package org.eclipse.ditto.services.connectivity.messaging;

import java.util.function.BinaryOperator;

/**
 * Interface providing methods for the different outcomes of a mapping - dropped, error and success. As a message may
 * either be the input for multiple mappings or a single mapping my produce multiple results, the defined methods may
 * be called multiple times for a single processed message. Multiple mappers are independent, the result of mapper A
 * does not influence the result of mapper B. E.g. mapper A fails but mapper B is successful is a valid case.
 *
 * @param <T> the type of successfully mapped messages
 * @param <R> the type of results of the handler
 */
interface MappingResultHandler<T, R> {

    /**
     * Is called when the mapping was successful.
     *
     * @param mappedMessage the successfully mapped message
     * @return result for a mapped message.
     */
    R onMessageMapped(T mappedMessage);

    /**
     * Is called when the mapping produced no result i.e. message should be dropped.
     *
     * @return result for a dropped message.
     */
    R onMessageDropped();

    /**
     * Is called when the mapping failed.
     *
     * @param ex the exception that was thrown by the mapper
     */
    R onException(Exception ex);

    /**
     * Combine 2 results into 1 to cater to multiple mapping results produced from one input message.
     *
     * @param left the first result.
     * @param right the second result.
     * @return the combined result.
     */
    R combineResults(R left, R right);

    /**
     * Create an empty result. Should be the left and right identiy of the
     *
     * @return the empty result.
     */
    R emptyResult();
}
