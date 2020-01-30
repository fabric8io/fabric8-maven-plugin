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
package io.fabric8.maven.generator.api.support;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.RuntimeMode;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.PrefixedLogger;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.generator.api.FromSelector;
import io.fabric8.maven.generator.api.Generator;
import io.fabric8.maven.generator.api.GeneratorConfig;
import io.fabric8.maven.generator.api.GeneratorContext;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.StringUtils;

/**
 * @author roland
 * @since 15/05/16
 */
abstract public class BaseGenerator implements Generator {
    private static final String BASE_IMAGE_LOOKUP_PREFIX = "java";

    protected static final String FABRIC8_GENERATOR_NAME = "fabric8.generator.name";
    protected static final String FABRIC8_GENERATOR_REGISTRY = "fabric8.generator.registry";
    protected static final String FABRIC8_GENERATOR_ALIAS = "fabric8.generator.alias";
    protected static final String FABRIC8_GENERATOR_FROM = "fabric8.generator.from";
    protected static final String FABRIC8_GENERATOR_FROM_MODE = "fabric8.generator.fromMode";

    private final GeneratorContext context;
    private final String name;
    private final GeneratorConfig config;
    protected final PrefixedLogger log;
    private final FromSelector fromSelector;

    private enum Config implements Configs.Key {
        // The image name
        name,

        // The alias to use (default to the generator name)
        alias,

        // whether the generator should always add to already existing image configurationws
        add {{d = "false"; }},

        // Base image
        from,

        // Base image mode (only relevant for OpenShift)
        fromMode,

        // Optional registry
        registry;

        public String def() { return d; } protected String d;
    }

    public BaseGenerator(GeneratorContext context, String name) {
        this.context = context;
        this.name = name;
        this.config = new GeneratorConfig(context.getProject().getProperties(), getName(), context.getConfig());
        this.log = new PrefixedLogger(name, context.getLogger());
        this.fromSelector = new FromSelector.Default(context, BASE_IMAGE_LOOKUP_PREFIX, log);
    }

    public BaseGenerator(GeneratorContext context, String name, FromSelector selector) {
        this.context = context;
        this.name = name;
        this.config = new GeneratorConfig(context.getProject().getProperties(), getName(), context.getConfig());
        this.log = new PrefixedLogger(name, context.getLogger());
        this.fromSelector = selector;
    }

    protected MavenProject getProject() {
        return context.getProject();
    }

    public String getName() {
        return name;
    }

    public GeneratorContext getContext() {
        return context;
    }

    protected String getConfig(Configs.Key key) {
        return config.get(key);
    }

    protected String getConfig(Configs.Key key, String defaultVal) {
        return config.get(key, defaultVal);
    }

    // Get 'from' as configured without any default and image stream tag handling
    protected String getFromAsConfigured() {
        return getConfigWithFallback(Config.from, FABRIC8_GENERATOR_FROM, null);
    }

    /**
     * Add the base image either from configuration or from a given selector
     *
     * @param builder for the build image configuration to add the from to.
     */
    protected void addFrom(BuildImageConfiguration.Builder builder) {
        String fromMode = getConfigWithFallback(Config.fromMode, FABRIC8_GENERATOR_FROM_MODE, getFromModeDefault(context.getRuntimeMode()));
        String from = getConfigWithFallback(Config.from, FABRIC8_GENERATOR_FROM, null);
        if ("docker".equalsIgnoreCase(fromMode)) {
            String fromImage = from;
            if (fromImage == null) {
                fromImage = fromSelector != null ? fromSelector.getFrom() : null;
            }
            builder.from(fromImage);
            log.info("Using Container image %s as base / builder", fromImage);
        } else if ("istag".equalsIgnoreCase(fromMode)) {
            Map<String, String> fromExt = new HashMap<>();
            if (from != null) {
                ImageName iName = new ImageName(from);
                // user/project is considered to be the namespace
                String tag = iName.getTag();
                if (StringUtils.isBlank(tag)) {
                    tag = "latest";
                }
                fromExt.put(OpenShiftBuildStrategy.SourceStrategy.name.key(), iName.getSimpleName() + ":" + tag);
                if (iName.getUser() != null) {
                    fromExt.put(OpenShiftBuildStrategy.SourceStrategy.namespace.key(), iName.getUser());
                }
                fromExt.put(OpenShiftBuildStrategy.SourceStrategy.kind.key(), "ImageStreamTag");
            } else {
                fromExt = fromSelector != null ? fromSelector.getImageStreamTagFromExt() : null;
            }
            if (fromExt != null) {
                String namespace = fromExt.get(OpenShiftBuildStrategy.SourceStrategy.namespace.key());
                if (namespace != null) {
                    log.info("Using ImageStreamTag '%s' from namespace '%s' as builder image",
                             fromExt.get(OpenShiftBuildStrategy.SourceStrategy.name.key()), namespace);
                } else {
                    log.info("Using ImageStreamTag '%s' as builder image",
                             fromExt.get(OpenShiftBuildStrategy.SourceStrategy.name.key()));
                }
                builder.fromExt(fromExt);
            }
        } else {
            throw new IllegalArgumentException(String.format("Invalid 'fromMode' in generator configuration for '%s'", getName()));
        }
    }

    // Use "istag" as default for "redhat" versions of this plugin
    private String getFromModeDefault(RuntimeMode mode) {
        if (mode == RuntimeMode.openshift && fromSelector != null && fromSelector.isRedHat()) {
            return "istag";
        } else {
            return "docker";
        }
    }

    /**
     * Get Image name with a standard default
     *
     * @return Docker image name which is never null
     */
    protected String getImageName() {
        if (RuntimeMode.isOpenShiftMode(getProject().getProperties())) {
            return getConfigWithFallback(Config.name, FABRIC8_GENERATOR_NAME, "%a:%l");
        } else {
            return getConfigWithFallback(Config.name, FABRIC8_GENERATOR_NAME, "%g/%a:%l");
        }
    }

    /**
     * Get the docker registry where the image should be located.
     * It returns null in Openshift mode.
     *
     * @return The docker registry if configured
     */
    protected String getRegistry() {
        if (!RuntimeMode.isOpenShiftMode(getProject().getProperties())) {
            return getConfigWithFallback(Config.registry, FABRIC8_GENERATOR_REGISTRY, null);
        }

        return null;
    }

    /**
     * Get alias name with the generator name as default
     * @return an alias which is never null;
     */
    protected String getAlias() {
        return getConfigWithFallback(Config.alias, FABRIC8_GENERATOR_ALIAS, getName());
    }

    protected boolean shouldAddImageConfiguration(List<ImageConfiguration> configs) {
        return !containsBuildConfiguration(configs) || Configs.asBoolean(getConfig(Config.add));
    }

    protected String getConfigWithFallback(Config name, String key, String defaultVal) {
        String value = getConfig(name);
        if (value == null) {
            value = Configs.getSystemPropertyWithMavenPropertyAsFallback(getProject().getProperties(), key);
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
