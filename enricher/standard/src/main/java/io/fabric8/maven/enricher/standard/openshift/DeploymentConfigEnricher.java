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

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import io.fabric8.openshift.api.model.DeploymentConfigSpec;
import io.fabric8.openshift.api.model.DeploymentConfigSpecBuilder;
import io.fabric8.openshift.api.model.DeploymentStrategy;
import io.fabric8.openshift.api.model.DeploymentStrategyBuilder;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;

import static io.fabric8.maven.core.util.kubernetes.KubernetesResourceUtil.removeItemFromKubernetesBuilder;

public class DeploymentConfigEnricher extends BaseEnricher {
    static final String ENRICHER_NAME = "fmp-openshift-deploymentconfig";
    private Boolean enableAutomaticTrigger;
    private Long openshiftDeployTimeoutSeconds;

    public DeploymentConfigEnricher(MavenEnricherContext context) {
        super(context, ENRICHER_NAME);
        this.enableAutomaticTrigger = getValueFromConfig(OPENSHIFT_ENABLE_AUTOMATIC_TRIGGER, true);
        this.openshiftDeployTimeoutSeconds = getOpenshiftDeployTimeoutInSeconds(3600L);
    }

    @Override
    public void create(PlatformMode platformMode, KubernetesListBuilder builder) {

        if(platformMode == PlatformMode.openshift) {

            for(HasMetadata item : builder.buildItems()) {
                if(item instanceof Deployment && !useDeploymentForOpenShift()) {
                    DeploymentConfig deploymentConfig = convertFromAppsV1Deployment(item);
                    removeItemFromKubernetesBuilder(builder, item);
                    builder.addToItems(deploymentConfig);
                } else if (item instanceof io.fabric8.kubernetes.api.model.extensions.Deployment && !useDeploymentForOpenShift()) {
                    DeploymentConfig deploymentConfig = convertFromExtensionsV1Beta1Deployment(item);
                    removeItemFromKubernetesBuilder(builder, item);
                    builder.addToItems(deploymentConfig);
                }
            }
        }
    }

    private DeploymentConfig convertFromAppsV1Deployment(HasMetadata item) {
        Deployment resource = (Deployment) item;
        DeploymentConfigBuilder builder = new DeploymentConfigBuilder();
        builder.withMetadata(resource.getMetadata());
        DeploymentSpec spec = resource.getSpec();
        if (spec != null) {
            builder.withSpec(getDeploymentConfigSpec(spec.getReplicas(), spec.getRevisionHistoryLimit(), spec.getSelector(), spec.getTemplate(), spec.getStrategy() != null ? spec.getStrategy().getType() : null));
        }
        return builder.build();
    }

    private DeploymentConfig convertFromExtensionsV1Beta1Deployment(HasMetadata item) {
        io.fabric8.kubernetes.api.model.extensions.Deployment resource = (io.fabric8.kubernetes.api.model.extensions.Deployment) item;
        DeploymentConfigBuilder builder = new DeploymentConfigBuilder();
        builder.withMetadata(resource.getMetadata());
        io.fabric8.kubernetes.api.model.extensions.DeploymentSpec spec = resource.getSpec();
        if (spec != null) {
            builder.withSpec(getDeploymentConfigSpec(spec.getReplicas(), spec.getRevisionHistoryLimit(), spec.getSelector(), spec.getTemplate(), spec.getStrategy() != null ? spec.getStrategy().getType() : null));
        }
        return builder.build();
    }

    private DeploymentConfigSpec getDeploymentConfigSpec(Integer replicas, Integer revisionHistoryLimit, LabelSelector selector, PodTemplateSpec podTemplateSpec, String strategyType) {
        DeploymentConfigSpecBuilder specBuilder = new DeploymentConfigSpecBuilder();
        if (replicas != null) {
            specBuilder.withReplicas(replicas);
        }
        if (revisionHistoryLimit != null) {
            specBuilder.withRevisionHistoryLimit(revisionHistoryLimit);
        }

        if (selector  != null) {
            Map<String, String> matchLabels = selector.getMatchLabels();
            if (matchLabels != null && !matchLabels.isEmpty()) {
                specBuilder.withSelector(matchLabels);
            }
        }
        if (podTemplateSpec != null) {
            specBuilder.withTemplate(podTemplateSpec);
            PodSpec podSpec = podTemplateSpec.getSpec();
            Objects.requireNonNull(podSpec, "No PodSpec for PodTemplate:" + podTemplateSpec);
            Objects.requireNonNull(podSpec, "No containers for PodTemplate.spec: " + podTemplateSpec);
        }
        io.fabric8.openshift.api.model.DeploymentStrategy deploymentStrategy = getDeploymentStrategy(strategyType);
        if (deploymentStrategy != null) {
            specBuilder.withStrategy(deploymentStrategy);
        }

        if(enableAutomaticTrigger.equals(Boolean.TRUE)) {
            specBuilder.addNewTrigger().withType("ConfigChange").endTrigger();
        }

        return specBuilder.build();
    }

    private DeploymentStrategy getDeploymentStrategy(String strategyType) {
        if (openshiftDeployTimeoutSeconds != null && openshiftDeployTimeoutSeconds > 0) {
            if (StringUtils.isBlank(strategyType) || "Rolling".equals(strategyType)) {
                return new DeploymentStrategyBuilder().withType("Rolling").
                        withNewRollingParams().withTimeoutSeconds(openshiftDeployTimeoutSeconds).endRollingParams().build();
            } else if ("Recreate".equals(strategyType)) {
                return new DeploymentStrategyBuilder().withType("Recreate").
                        withNewRecreateParams().withTimeoutSeconds(openshiftDeployTimeoutSeconds).endRecreateParams().build();
            } else {
                return new DeploymentStrategyBuilder().withType(strategyType).build();
            }
        } else if (StringUtils.isNotBlank(strategyType)) {
            return new DeploymentStrategyBuilder().withType(strategyType).build();
        }
        return null;
    }
}
