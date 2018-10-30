/**
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
package io.fabric8.maven.enricher.api;

import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.RuntimeMode;
import io.fabric8.maven.core.model.Configuration;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.PrefixedLogger;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * @author roland
 * @since 01/04/16
 */
public abstract class BaseEnricher implements Enricher {

    private final EnricherConfig config;
    private final String name;
    public EnricherContext enricherContext;

    protected Logger log;

    public BaseEnricher(EnricherContext enricherContext, String name) {
        this.enricherContext = enricherContext;
        // Pick the configuration which is for us
        this.config = new EnricherConfig(name, enricherContext.getConfiguration());
        this.log = new PrefixedLogger(name, enricherContext.getLog());
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void enrich(PlatformMode platformMode, KubernetesListBuilder builder) { }

    @Override
    public void create(PlatformMode platformMode, KubernetesListBuilder builder) { }

    protected Logger getLog() {
        return log;
    }

    protected Optional<List<ImageConfiguration>> getImages() {
        return enricherContext.getConfiguration().getImages();
    }

    protected boolean hasImageConfiguration() {
        return !enricherContext.getConfiguration().getImages().orElse(Collections.emptyList()).isEmpty();
    }

    protected Configuration getConfiguration() {
        return enricherContext.getConfiguration();
    }

    protected String getConfig(Configs.Key key) {
        return config.get(key);
    }

    protected boolean hasConfig(Configs.Key key) {
        return config.get(key) != null;
    }

    protected String getConfig(Configs.Key key, String defaultVal) {
        return config.get(key, defaultVal);
    }

    protected Map<String, String> getRawConfig() {
        return config.getRawConfig();
    }

    protected EnricherContext getContext() {
        return enricherContext;
    }

    /**
     * Returns true if we are in OpenShift S2I binary building mode
     */
    protected boolean isOpenShiftMode() {
        Properties properties = getContext().getConfiguration().getProperties();
        if (properties != null) {
            return RuntimeMode.isOpenShiftMode(properties);
        }
        return false;
    }
}
