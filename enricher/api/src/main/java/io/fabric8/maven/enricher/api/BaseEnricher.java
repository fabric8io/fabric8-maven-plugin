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

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.maven.core.config.ResourceConfiguration;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.utils.Strings;
import org.apache.maven.project.MavenProject;

import java.util.List;
import java.util.Map;

import static io.fabric8.kubernetes.api.KubernetesHelper.DEFAULT_NAMESPACE;

/**
 * @author roland
 * @since 01/04/16
 */
public abstract class BaseEnricher implements Enricher {

    private final EnricherConfiguration config;
    private final String name;
    private EnricherContext buildContext;

    protected Logger log;

    public BaseEnricher(EnricherContext buildContext, String name) {
        this.buildContext = buildContext;
        // Pick the configuration which is for us
        this.config = new EnricherConfiguration(buildContext.getProject().getProperties(),
                                                name, buildContext.getConfig());
        this.log = buildContext.getLog();
        this.name = name;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public Map<String, String> getLabels(Kind kind) { return null; }

    @Override
    public Map<String, String> getAnnotations(Kind kind) { return null; }

    @Override
    public void adapt(KubernetesListBuilder builder) { }

    @Override
    public void addDefaultResources(KubernetesListBuilder builder) { }

    @Override
    public Map<String, String> getSelector(Kind kind) { return null; }
    protected MavenProject getProject() {
        return buildContext.getProject();
    }

    protected Logger getLog() {
        return log;
    }

    protected List<ImageConfiguration> getImages() {
        return buildContext.getImages();
    }

    protected String getConfig(Configs.Key key) {
        return config.get(key);
    }

    protected String getConfig(Configs.Key key, String defaultVal) {
        return config.get(key, defaultVal);
    }

    protected EnricherContext getContext() {
        return buildContext;
    }
}
