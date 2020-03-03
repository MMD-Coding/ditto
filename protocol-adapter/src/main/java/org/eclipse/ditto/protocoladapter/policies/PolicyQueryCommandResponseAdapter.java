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
package org.eclipse.ditto.protocoladapter.policies;

import static java.util.Objects.requireNonNull;

import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.QueryCommandResponseAdapter;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.adaptables.MappingStrategiesFactory;
import org.eclipse.ditto.protocoladapter.signals.SignalMapperFactory;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommandResponse;

/**
 * Adapter for mapping a {@link PolicyQueryCommandResponse} to and from an {@link Adaptable}.
 */
final class PolicyQueryCommandResponseAdapter extends AbstractPolicyAdapter<PolicyQueryCommandResponse<?>>
        implements QueryCommandResponseAdapter<PolicyQueryCommandResponse<?>> {

    private PolicyQueryCommandResponseAdapter(
            final HeaderTranslator headerTranslator) {
        super(MappingStrategiesFactory.getPolicyQueryCommandResponseMappingStrategies(),
                SignalMapperFactory.newPolicyQueryResponseSignalMapper(), headerTranslator
        );
    }

    /**
     * Returns a new PolicyQueryCommandResponseAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static PolicyQueryCommandResponseAdapter of(final HeaderTranslator headerTranslator) {
        return new PolicyQueryCommandResponseAdapter(requireNonNull(headerTranslator));
    }

    @Override
    protected String getTypeCriterionAsString(final TopicPath topicPath) {
        return RESPONSES_CRITERION;
    }
}
