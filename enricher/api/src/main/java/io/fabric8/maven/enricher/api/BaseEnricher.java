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

package io.fabric8.maven.enricher.api;

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.project.MavenProject;

/**
 * @author roland
 * @since 01/04/16
 */
public abstract class BaseEnricher implements Enricher {

    private final EnricherConfiguration config;
    private EnricherContext buildContext;

    protected Logger log;

    public BaseEnricher(EnricherContext buildContext) {
        this.buildContext = buildContext;
        // Pick the configuration which is for us
        this.config = new EnricherConfiguration(getName(), buildContext.getConfig());
        this.log = buildContext.getLog();
    }

    protected MavenProject getProject() {
        return buildContext.getProject();
    }

    protected List<ImageConfiguration> getImages() {
        return buildContext.getImages();
    }

    protected String getConfig(EnricherConfiguration.ConfigKey key) {
        return config.get(key);
    }

    protected String getConfig(EnricherConfiguration.ConfigKey key, String defaultVal) {
        return config.get(key, defaultVal);
    }

    protected int asInt(String value) {
        return value != null ? Integer.parseInt(value) : 0;
    }

    protected boolean asBoolean(String value) {
        return value != null ? Boolean.parseBoolean(value) : false;
    }

    protected EnricherContext getBuildContext() {
        return buildContext;
    }

    @Override
    public Map<String, String> getLabels(Kind kind) { return null; }

    @Override
    public Map<String, String> getAnnotations(Kind kind) { return null; }

    @Override
    public void enrich(KubernetesListBuilder builder) { }

    @Override
    public Map<String, String> getSelector(Kind kind) { return null; }
}
