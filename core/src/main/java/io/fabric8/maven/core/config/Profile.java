/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.fabric8.maven.core.config;

import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A profile is a named configuration with ernicher and generator configs.
 *
 * @author roland
 * @since 24/07/16
 */
public class Profile implements Comparable<Profile> {

    // Create id for each object
    private static final AtomicInteger idCreator = new AtomicInteger(0);
    private final int id;

    /**
     * Profile name
     */
    @JsonProperty(value = "name")
    private String name;

    /**
     * Enricher configurations
     */
    @JsonProperty(value = "enricher")
    private ProcessorConfig enricherConfig;

    /**
     * Generator configurations
     */
    @JsonProperty(value = "generator")
    private ProcessorConfig generatorConfig;

    /**
     * An order in case multiple profiles are found
     * with the same name
     */
    @JsonProperty(value = "order")
    private int order;

    // No-arg constructor for YAML deserialization
    public Profile() {
        this.id = idCreator.getAndIncrement();
    }

    // Copy constructor
    public Profile(Profile profile) {
        this();
        this.name = profile.name;
        this.order = profile.order;
        this.enricherConfig = ProcessorConfig.mergeProcessorConfigs(profile.enricherConfig);
        this.generatorConfig = ProcessorConfig.mergeProcessorConfigs(profile.generatorConfig);
    }

    // Merge constructor
    public Profile(Profile profileA, Profile profileB) {
        this();
        this.name = profileA.name;
        if (!profileB.name.equals(profileA.getName())) {
            throw new IllegalArgumentException(String.format("Cannot merge to profiles with different names (%s vs. %s)", profileA.getName(), profileB.getName()));
        }
        // Respect order: The higher order overrides the smaller order. If equal, use the argument order given.
        if (profileA.order > profileB.order) {
            this.order = profileA.order;
            this.enricherConfig = ProcessorConfig.mergeProcessorConfigs(profileB.enricherConfig, profileA.enricherConfig);
            this.generatorConfig = ProcessorConfig.mergeProcessorConfigs(profileB.generatorConfig, profileA.generatorConfig);
        } else {
            this.order = profileB.order;
            this.enricherConfig = ProcessorConfig.mergeProcessorConfigs(profileA.enricherConfig, profileB.enricherConfig);
            this.generatorConfig = ProcessorConfig.mergeProcessorConfigs(profileA.generatorConfig, profileB.generatorConfig);
        }
    }

    public String getName() {
        return name;
    }

    public ProcessorConfig getEnricherConfig() {
        return enricherConfig;
    }

    public ProcessorConfig getGeneratorConfig() {
        return generatorConfig;
    }

    public int getOrder() {
        return order;
    }

    @Override
    public int compareTo(Profile o) {
        int orderDiff = order - o.order;
        if (orderDiff != 0) {
            return orderDiff;
        } else {
            return id - o.id;
        }
    }
}
