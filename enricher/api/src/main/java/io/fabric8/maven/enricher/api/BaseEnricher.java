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

import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.PrefixedLogger;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.utils.Strings;
import org.apache.maven.project.MavenProject;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author roland
 * @since 01/04/16
 */
public abstract class BaseEnricher implements Enricher {

    public static final String INIT_CONTAINER_ANNOTATION = "pod.alpha.kubernetes.io/init-containers";

    private final EnricherConfig config;
    private final String name;
    private EnricherContext buildContext;

    protected Logger log;

    public BaseEnricher(EnricherContext buildContext, String name) {
        this.buildContext = buildContext;
        // Pick the configuration which is for us
        this.config = new EnricherConfig(buildContext.getProject().getProperties(),
                                         name, buildContext.getConfig());
        this.log = new PrefixedLogger(name, buildContext.getLog());
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Map<String, String> getLabels(Kind kind) { return null; }

    @Override
    public Map<String, String> getAnnotations(Kind kind) { return null; }

    @Override
    public void adapt(KubernetesListBuilder builder) { }

    @Override
    public void addMissingResources(KubernetesListBuilder builder) { }

    @Override
    public Map<String, String> getSelector(Kind kind) { return null; }

    protected MavenProject getProject() {
        if (buildContext != null) {
            return buildContext.getProject();
        }
        return null;
    }

    protected Logger getLog() {
        return log;
    }


    protected List<ImageConfiguration> getImages() {
        return buildContext.getImages();
    }

    protected boolean hasImageConfiguration() {
        return !buildContext.getImages().isEmpty();
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

    /**
     * Returns true if we are in OpenShift S2I binary building mode
     */
    protected boolean isOpenShiftMode() {
        MavenProject project = getProject();
        if (project != null) {
            Properties properties = project.getProperties();
            if (properties != null) {
                return PlatformMode.isOpenShiftMode(properties);
            }
        }
        return false;
    }

    protected void ensureMetadata(PodTemplateSpecBuilder obj) {
        ObjectMeta metadata = obj.buildMetadata();
        if (metadata == null) {
            obj.withNewMetadata().endMetadata();
        }
    }

    protected void addInitContainer(PodTemplateSpecBuilder builder, JSONObject initContainer) {
        ensureMetadata(builder);
        String initContainerAnnotation = builder.buildMetadata().getAnnotations().get(INIT_CONTAINER_ANNOTATION);

        JSONArray initContainers = Strings.isNullOrBlank(initContainerAnnotation) ? new JSONArray()
                : new JSONArray(initContainerAnnotation);
        initContainers.put(initContainer);

        builder.editMetadata().addToAnnotations(INIT_CONTAINER_ANNOTATION, initContainers.toString()).endMetadata();
    }
}
