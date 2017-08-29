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

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.PrefixedLogger;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.project.MavenProject;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.fabric8.maven.core.util.Constants.*;

/**
 * @author roland
 * @since 01/04/16
 */
public abstract class BaseEnricher implements Enricher {

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
}
