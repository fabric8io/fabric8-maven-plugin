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

package io.fabric8.maven.generator.api.support;

import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.PrefixedLogger;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.generator.api.FromSelector;
import io.fabric8.maven.generator.api.Generator;
import io.fabric8.maven.generator.api.GeneratorConfig;
import io.fabric8.maven.generator.api.MavenGeneratorContext;
import org.apache.maven.project.MavenProject;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * @author roland
 * @since 15/05/16
 */
abstract public class BaseGenerator implements Generator {

    private final MavenGeneratorContext context;
    private final String name;
    private final GeneratorConfig config;
    protected final PrefixedLogger log;
    private final FromSelector fromSelector;

    /**
     * Returns the maven project property or the default value
     */
    protected String getProjectProperty(String propertyName, String defaultValue) {
        MavenProject project = getProject();
        if (project != null) {
            Properties properties = project.getProperties();
            if (properties != null) {
                return properties.getProperty(propertyName, defaultValue);
            }
        }
        return defaultValue;
    }

    private enum Config implements Configs.Key {
        // Whether to merge in existing configuration or not
        merge,

        // The image name
        name,

        // The alias to use (default to the generator name)
        alias,

        // Base image
        from;

        public String def() { return d; } protected String d;

    }
    public BaseGenerator(MavenGeneratorContext context, String name) {
        this(context, name, null);
    }

    public BaseGenerator(MavenGeneratorContext context, String name, FromSelector fromSelector) {
        this.context = context;
        this.name = name;
        this.fromSelector = fromSelector;
        this.config = new GeneratorConfig(context.getProject().getProperties(), getName(), context.getConfig());
        this.log = new PrefixedLogger(name, context.getLog());
    }

    protected MavenProject getProject() {
        return context.getProject();
    }

    public String getName() {
        return name;
    }

    public MavenGeneratorContext getContext() {
        return context;
    }

    public GeneratorConfig getConfig() {
        return config;
    }

    protected String getConfig(Configs.Key key) {
        return config.get(key);
    }

    protected String getConfig(Configs.Key key, String defaultVal) {
        return config.get(key, defaultVal);
    }

    /**
     * Get base image either from configuration or from a given selector
     *
     * @return the base image or <code>null</code> when none could be detected.
     */
    protected String getFrom() {
        String from = getConfigWithSystemFallbackAndDefault(Config.from, "fabric8.generator.from", null);
        if (from != null) {
            return from;
        }
        return fromSelector != null ? fromSelector.getFrom() : null;
    }

    /**
     * Get Image name with a standard default
     *
     * @return Docker image name which is never null
     */
    protected String getImageName() {
        return getConfigWithSystemFallbackAndDefault(Config.name, "fabric8.generator.name", getDefaultImageUserExpression() + "%a:" + getDefaultImageLabelExpression());
    }

    private String getDefaultImageUserExpression() {
        if (PlatformMode.isOpenShiftMode(getProject().getProperties())) {
            return "";
        }
        return "%g/";
    }

    private String getDefaultImageLabelExpression() {
        if (PlatformMode.isOpenShiftMode(getProject().getProperties())) {
            return "%l";
        }
        return "%t";
    }

    /**
     * Get alias name with the generator name as default
     * @return an alias which is never null;
     */
    protected String getAlias() {
        return getConfigWithSystemFallbackAndDefault(Config.alias, "fabric8.generator.alias", getName());
    }

    protected boolean shouldAddDefaultImage(List<ImageConfiguration> configs) {
        return !containsBuildConfiguration(configs);
    }

    private String getConfigWithSystemFallbackAndDefault(Config name, String key, String defaultVal) {
        String value = getConfig(name);
        if (value == null) {
            value = Configs.getPropertyWithSystemAsFallback(getProject().getProperties(), key);
        }
        return value != null ? value : defaultVal;
    }
    protected void addLatestTagIfSnapshot(BuildImageConfiguration.Builder buildBuilder) {
        MavenProject project = getProject();
        if (project.getVersion().endsWith("-SNAPSHOT")) {
            buildBuilder.tags(Collections.singletonList("latest"));
        }
    }

    private boolean containsBuildConfiguration(List<ImageConfiguration> configs) {
        for (ImageConfiguration config : configs) {
            if (config.getBuildConfiguration() != null) {
                return true;
            }
        }
        return false;
    }

}
