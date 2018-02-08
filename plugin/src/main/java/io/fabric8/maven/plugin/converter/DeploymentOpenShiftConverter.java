package io.fabric8.maven.plugin.converter;
/*
 *
 * Copyright 2016 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.extensions.*;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import io.fabric8.openshift.api.model.DeploymentConfigFluent;

import static io.fabric8.utils.Objects.notNull;

/**
 * Convert a Kubernetes <code>Deployment</code> to an OpenShift <code>DeploymentConfig</code>
 *
 * @author roland
 * @since 01/08/16
 */
public class DeploymentOpenShiftConverter implements KubernetesToOpenShiftConverter {
    private final PlatformMode mode;
    private final Long openshiftDeployTimeoutSeconds;

    public DeploymentOpenShiftConverter(PlatformMode mode, Long openshiftDeployTimeoutSeconds) {
        this.mode = mode;
        this.openshiftDeployTimeoutSeconds = openshiftDeployTimeoutSeconds;
    }

    @Override
    public HasMetadata convert(HasMetadata item, boolean trimImageInContainerSpec) {
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
                    notNull(podSpec, "No PodSpec for PodTemplate:" + template);
                    List<Container> containers = podSpec.getContainers();
                    notNull(podSpec, "No containers for PodTemplate.spec: " + template);
                    for (Container container : containers) {
                        validateContainer(container);
                        containerToImageMap.put(container.getName(), container.getImage());
                    }
                }
                DeploymentStrategy strategy = spec.getStrategy();
                if (strategy != null) {
                    // TODO is there any values we can copy across?
                    //specBuilder.withStrategy(strategy);
                }
                if (openshiftDeployTimeoutSeconds != null && openshiftDeployTimeoutSeconds > 0) {
                    specBuilder.withNewStrategy().withType("Rolling").
                        withNewRollingParams().withTimeoutSeconds(openshiftDeployTimeoutSeconds).endRollingParams().endStrategy();
                }

                // lets add a default trigger so that its triggered when we change its config
                specBuilder.addNewTrigger().withType("ConfigChange").endTrigger();

                // add a new image change trigger for the build stream
                if (containerToImageMap.size() != 0) {
                    if (mode.equals(PlatformMode.openshift)) {
                        for (Map.Entry<String, String> entry : containerToImageMap.entrySet()) {
                            String containerName = entry.getKey();
                            ImageName image = new ImageName(entry.getValue());
                            specBuilder.addNewTrigger()
                                    .withType("ImageChange")
                                    .withNewImageChangeParams()
                                    .withAutomatic(true)
                                    .withNewFrom()
                                    .withKind("ImageStreamTag")
                                    .withName(image.getSimpleName() + ":" + image.getTag())
                                    .endFrom()
                                    .withContainerNames(containerName)
                                    .endImageChangeParams()
                                    .endTrigger();
                        }
                    }
                    if(trimImageInContainerSpec) {
                        /*
                         * In Openshift 3.7, update to container image is automatically triggering redeployments
                         * and those subsequent rollouts lead to RC complaining about a missing image reference.
                         *
                         *    See this : https://github.com/openshift/origin/issues/18406#issuecomment-364090247
                         *
                         * this the time it gets fixed. Do this:
                         * Since we're using ImageTrigger here, set container image to " ". If there is any config
                         * change never set to image else than " "; so doing oc apply/rollouts won't be creating
                         * re-deployments again and again.
                         *
                         */
                        List<Container> containers = template.getSpec().getContainers();
                        for (Integer nIndex = 0; nIndex < containers.size(); nIndex++) {
                            containers.get(nIndex).setImage(" ");
                        }
                        template.getSpec().setContainers(containers);
                        specBuilder.withTemplate(template);
                    }
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
