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
package org.eclipse.ditto.services.gateway.streaming;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Provides configuration settings of general gateway streaming functionality.
 */
public interface StreamingConfig {

    /**
     * Config path relative to its parent.
     */
    String CONFIG_PATH = "streaming";

    /**
     * Returns the session counter update interval.
     *
     * @return the interval.
     */
    Duration getSessionCounterScrapeInterval();

    /**
     * Returns the websocket config for streaming.
     *
     * @return the websocket config.
     */
    WebsocketConfig getWebsocketConfig();

    /**
     * Returns the provider of thing-enrichment facades.
     *
     * @return the class name of the facade provider.
     */
    String getThingEnrichmentProvider();

    /**
     * Returns the configuration for the thing-enrichment facade provider.
     *
     * @return the configuration.
     */
    Config getThingEnrichmentConfig();

    /**
     * Render this object into a Config object from which a copy of this object can be constructed.
     *
     * @return a config representation.
     */
    default Config render() {
        final Map<String, Object> map = new HashMap<>();
        map.put(StreamingConfigValue.SESSION_COUNTER_SCRAPE_INTERVAL.getConfigPath(),
                getSessionCounterScrapeInterval().toMillis() + "ms");
        map.put(StreamingConfigValue.THING_ENRICHMENT_PROVIDER.getConfigPath(), getThingEnrichmentProvider());
        map.put(StreamingConfigValue.THING_ENRICHMENT_CONFIG.getConfigPath(), getThingEnrichmentConfig().root());
        return ConfigFactory.parseMap(map)
                .withFallback(getWebsocketConfig().render())
                .atKey(CONFIG_PATH);
    }

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code StreamingConfig}.
     */
    enum StreamingConfigValue implements KnownConfigValue {

        /**
         * How often to update websocket session counter by counting child actors.
         */
        SESSION_COUNTER_SCRAPE_INTERVAL("session-counter-scrape-interval", Duration.ofSeconds(30L)),

        /**
         * Fully qualified name of the thing-enriching facade provider.
         */
        THING_ENRICHMENT_PROVIDER("thing-enrichment.provider", ""),

        /**
         * Config for the thing-enriching facade provider.
         */
        THING_ENRICHMENT_CONFIG("thing-enrichment.config", ConfigFactory.empty().root());

        private final String path;
        private final Object defaultValue;

        StreamingConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

    }
}
