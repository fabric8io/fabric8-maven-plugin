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

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.config.RuntimeMode;
import io.fabric8.maven.core.model.Configuration;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.PrefixedLogger;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.openshift.api.model.DeploymentConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * @author roland
 * @since 01/04/16
 */
public class BaseEnricher implements Enricher {

    private final EnricherConfig config;
    private final String name;
    protected EnricherContext enricherContext;
    public static final String FABRIC8_GENERATED_CONTAINERS = "FABRIC8_GENERATED_CONTAINERS";
    public static final String NEED_IMAGECHANGE_TRIGGERS = "IMAGECHANGE_TRIGGER";

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

    protected List<String> getProcessingInstructionViaKey(String key) {
        List<String> containers = new ArrayList<>();
        if(enricherContext.getProcessingInstructions() != null) {
            if(enricherContext.getProcessingInstructions().get(key) != null) {
                containers.addAll(Arrays.asList(enricherContext.getProcessingInstructions().get(key).split(",")));
            }
        }
        return containers;
    }

    protected Boolean isAutomaticTriggerEnabled(MavenEnricherContext enricherContext, Boolean defaultValue) {
        if(enricherContext.getProperty("fabric8.openshift.enableAutomaticTrigger") != null) {
            return Boolean.parseBoolean(enricherContext.getProperty("fabric8.openshift.enableAutomaticTrigger").toString());
        } else {
            return defaultValue;
        }
    }

    protected Long getOpenshiftDeployTimeoutInSeconds(MavenEnricherContext enricherContext, Long defaultValue) {
        if (enricherContext.getProperty("fabric8.openshift.deployTimeoutSeconds") != null) {
            return Long.parseLong(enricherContext.getProperty("fabric8.openshift.deployTimeoutSeconds").toString());
        } else {
            return defaultValue;
        }
    }

    protected Boolean getImageChangeTriggerFlag(Boolean defaultValue) {
        if (getContext().getProperty("fabric8.openshift.imageChangeTriggers") != null) {
            return Boolean.parseBoolean(getContext().getProperty("fabric8.openshift.imageChangeTriggers").toString());
        } else {
            return defaultValue;
        }
    }

    protected Boolean getTrimImageInContainerSpecFlag(Boolean defaultValue) {
        if (getContext().getProperty("fabric8.openshift.trimImageInContainerSpec") != null) {
            return Boolean.parseBoolean(getContext().getProperty("fabric8.openshift.trimImageInContainerSpec").toString());
        } else {
            return defaultValue;
        }
    }

    /**
     * This method overrides the ImagePullPolicy value by the value provided in
     * XML config.
     *
     * @param resourceConfig
     * @param defaultValue
     * @return
     */
    protected String getImagePullPolicy(ResourceConfig resourceConfig, String defaultValue) {
        if(resourceConfig != null) {
            return resourceConfig.getImagePullPolicy() != null ? resourceConfig.getImagePullPolicy() : defaultValue;
        }
        return defaultValue;
    }

    /**
     * This method just makes sure that the replica count provided in XML config
     * overrides the default option; and resource fragments are always given
     * topmost priority.
     *
     * @param builder
     * @param xmlResourceConfig
     * @param defaultValue
     * @return resolved replica count
     */
    protected int getReplicaCount(KubernetesListBuilder builder, ResourceConfig xmlResourceConfig, int defaultValue) {
        if (xmlResourceConfig != null) {
            List<HasMetadata> items = builder.buildItems();
            for (HasMetadata item : items) {
                if (item instanceof Deployment) {
                    if(((Deployment)item).getSpec().getReplicas() != null) {
                        return ((Deployment)item).getSpec().getReplicas();
                    }
                }
                if (item instanceof DeploymentConfig) {
                    if(((DeploymentConfig)item).getSpec().getReplicas() != null) {
                        return ((DeploymentConfig)item).getSpec().getReplicas();
                    }
                }
            }
            return xmlResourceConfig.getReplicas() > 0 ? xmlResourceConfig.getReplicas() : defaultValue;
        }
        return defaultValue;
    }

    protected void setProcessingInstruction(String key, List<String> containerNames) {
        Map<String, String> processingInstructionsMap = new HashMap<>();
        if(enricherContext.getProcessingInstructions() != null) {
            processingInstructionsMap.putAll(enricherContext.getProcessingInstructions());
        }
        processingInstructionsMap.put(key, String.join(",", containerNames));
        enricherContext.setProcessingInstructions(processingInstructionsMap);
    }
}
