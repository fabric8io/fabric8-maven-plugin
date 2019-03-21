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
package io.fabric8.maven.enricher.standard.openshift;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentStrategy;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import io.fabric8.openshift.api.model.DeploymentConfigFluent;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DeploymentConfigEnricher extends BaseEnricher {
    static final String ENRICHER_NAME = "fmp-openshift-deploymentconfig";
    private Boolean enableAutomaticTrigger;
    private Boolean enableImageChangeTrigger;
    private Long openshiftDeployTimeoutSeconds;

    public DeploymentConfigEnricher(MavenEnricherContext context) {
        super(context, ENRICHER_NAME);
        this.enableAutomaticTrigger = isAutomaticTriggerEnabled(context, true);
        this.enableImageChangeTrigger = getImageChangeTriggerFlag(true);
        this.openshiftDeployTimeoutSeconds = getOpenshiftDeployTimeoutInSeconds(context, 3600L);
    }

    @Override
    public void create(PlatformMode platformMode, KubernetesListBuilder builder) {
        if(platformMode == PlatformMode.openshift) {
            for(HasMetadata item : builder.buildItems()) {
                if(item instanceof Deployment) {
                    DeploymentConfig deploymentConfig = convert(item, isOpenShiftMode());
                    removeItemFromBuilder(builder, item);
                    builder.addToDeploymentConfigItems(deploymentConfig);
                }
            }
        }
    }

    private void removeItemFromBuilder(KubernetesListBuilder builder, HasMetadata item) {
        List<HasMetadata> items = builder.buildItems();
        List<HasMetadata> newListItems = new ArrayList<>();
        for(HasMetadata listItem : items) {
            if(!listItem.equals(item)) {
                newListItems.add(listItem);
            }
        }
        builder.withItems(newListItems);
    }

    private DeploymentConfig convert(HasMetadata item, Boolean isOpenshiftBuildStrategy) {
        Deployment resource = (Deployment) item;
        DeploymentConfigBuilder builder = new DeploymentConfigBuilder();
        builder.withMetadata(resource.getMetadata());
        DeploymentSpec spec = resource.getSpec();
        if (spec != null) {
            DeploymentConfigFluent.SpecNested<DeploymentConfigBuilder> specBuilder = builder.withNewSpec();
            Integer replicas = spec.getReplicas();
            if (replicas != null) {
                specBuilder.withReplicas(replicas);
            }
            Integer revisionHistoryLimit = spec.getRevisionHistoryLimit();
            if (revisionHistoryLimit != null) {
                specBuilder.withRevisionHistoryLimit(revisionHistoryLimit);
            }

            LabelSelector selector = spec.getSelector();
            if (selector  != null) {
                Map<String, String> matchLabels = selector.getMatchLabels();
                if (matchLabels != null && !matchLabels.isEmpty()) {
                    specBuilder.withSelector(matchLabels);
                }
            }
            Map<String, String> containerToImageMap = new HashMap<>();
            PodTemplateSpec template = spec.getTemplate();
            if (template != null) {
                specBuilder.withTemplate(template);
                PodSpec podSpec = template.getSpec();
                Objects.requireNonNull(podSpec, "No PodSpec for PodTemplate:" + template);
                List<Container> containers = podSpec.getContainers();
                Objects.requireNonNull(podSpec, "No containers for PodTemplate.spec: " + template);
                for (Container container : containers) {
                    validateContainer(container);
                    containerToImageMap.put(container.getName(), container.getImage());
                }
            }
            DeploymentStrategy strategy = spec.getStrategy();
            String strategyType = null;
            if (strategy != null) {
                strategyType = strategy.getType();
            }
            if (openshiftDeployTimeoutSeconds != null && openshiftDeployTimeoutSeconds > 0) {
                if (StringUtils.isBlank(strategyType) || "Rolling".equals(strategyType)) {
                    specBuilder.withNewStrategy().withType("Rolling").
                            withNewRollingParams().withTimeoutSeconds(openshiftDeployTimeoutSeconds).endRollingParams().endStrategy();
                } else if ("Recreate".equals(strategyType)) {
                    specBuilder.withNewStrategy().withType("Recreate").
                            withNewRecreateParams().withTimeoutSeconds(openshiftDeployTimeoutSeconds).endRecreateParams().endStrategy();
                } else {
                    specBuilder.withNewStrategy().withType(strategyType).endStrategy();
                }
            } else if (StringUtils.isNotBlank(strategyType)) {
                // TODO is there any values we can copy across?
                specBuilder.withNewStrategy().withType(strategyType).endStrategy();
            }

            if(enableAutomaticTrigger) {
                specBuilder.addNewTrigger().withType("ConfigChange").endTrigger();
            }

            specBuilder.endSpec();
        }
        return builder.build();
    }


    private void validateContainer(Container container) {
        if (container.getImage() == null) {
            throw new IllegalArgumentException("Container " + container.getName() + " has no Docker image configured. " +
                    "Please check your Docker image configuration (including the generators which are supposed to run)");
        }
    }
}
