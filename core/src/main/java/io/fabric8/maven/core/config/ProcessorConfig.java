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

import java.util.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Configuration for enrichers and generators
 *
 * @author roland
 * @since 24/07/16
 */
public class ProcessorConfig {

    public static final ProcessorConfig EMPTY = new ProcessorConfig();

    /**
     * Modules to includes, should holde <code>&lt;include&gt;</code> elements
     */
    @Parameter
    @JsonProperty(value = "includes")
    private List<String> includes;

    /**
     * Modules to excludes, should hold <code>&lt;exclude&gt;</code> elements
     */
    @Parameter
    @JsonProperty(value = "excludes")
    private Set<String> excludes;

    /**
     * Configuration for enricher / generators
     */
    // See http://stackoverflow.com/questions/38628399/using-map-of-maps-as-maven-plugin-parameters/38642613 why
    // a "TreeMap" is used as parameter and not "Map<String, String>"
    @Parameter
    @JsonProperty(value = "config")
    Map<String, TreeMap> config = new HashMap<>();

    public ProcessorConfig() { }

    public ProcessorConfig(List<String> includes, Set<String> excludes, Map<String, TreeMap> config) {
        this.includes = includes;
        this.excludes = excludes;
        if (config != null) {
            this.config = config;
        }
    }

    public String getConfig(String name, String key) {
        TreeMap processorMap =  config.get(name);
        return processorMap != null ? (String) processorMap.get(key) : null;
    }

    /**
     * Check whether the given name is to be used according to the includes and excludes
     * given.
     *
     * <ul>
     *     <li>If "includes" are set, check whether name is in this list and return true if so</li>
     *     <li>If "excludes" are set, check whether name is in this list and return false if so</li>
     *     <li>If neither of this is true, check whether "includes" were given. When yes, return false, otherwise
     *         return true;</li>.
     * </ul>
     *
     * This implies that includes always have precedence over excludes.
     *
     * @param name the name to check
     * @return true if the processor with this name should be used, false otherwise.
     */
    public boolean use(String name) {
        if (includes != null && includes.contains(name)) {
            return true;
        } else if (excludes != null && excludes.contains(name)) {
            return false;
        } else {
            return includes != null ? false : true;
        }
    }

    /**
     * Order elements according to the order provided by the include statements.
     * If no includes has been configured, return the given list unaltered.
     * Otherwise arrange the elements from the list in to the include order and return a new
     * list.
     *
     * If an include specifies an element which does not exist, an exception is thrown.
     *
     * @param namedList the list to order
     * @param type a description used in an error message (like 'generator' or 'enricher')
     * @param <T> the concrete type
     * @return the ordered list according to the algorithm described above
     * @throws IllegalArgumentException if the includes reference an non existing element
     */
    public <T extends Named> List<T> order(List<T> namedList, String type) {
        if (includes == null) {
            return namedList;
        }
        List<T> ret = new ArrayList<>();
        Map<String, T> lookup = new HashMap<>();
        for (T named : namedList) {
            lookup.put(named.getName(), named);
        }
        for (String inc : includes) {
            T named = lookup.get(inc);
            if (named == null) {
                throw new IllegalArgumentException("No " + type + " with name '" + inc +
                                                   "' found to include. " +
                                                   "Please check spelling and your project dependencies");
            }
            ret.add(named);
        }
        return ret;
    }
}
