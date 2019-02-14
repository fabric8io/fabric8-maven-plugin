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
package io.fabric8.maven.core.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.config.RuntimeMode;
import io.fabric8.maven.core.util.kubernetes.KubernetesHelper;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.openshift.api.model.*;

public class DeploymentConfigHandler {
    private final PodTemplateHandler podTemplateHandler;
    DeploymentConfigHandler(PodTemplateHandler podTemplateHandler) {
        this.podTemplateHandler = podTemplateHandler;
    }

    public DeploymentConfig getDeploymentConfig(ResourceConfig config,
                                                List<ImageConfiguration> images, Long openshiftDeployTimeoutSeconds, RuntimeMode runtimeMode, Boolean enableAutomaticTrigger) {

        DeploymentConfig deploymentConfig = new DeploymentConfigBuilder()
                .withMetadata(createDeploymentConfigMetaData(config))
                .withSpec(createDeploymentConfigSpec(config, images, openshiftDeployTimeoutSeconds, runtimeMode, enableAutomaticTrigger))
                .build();

        return deploymentConfig;
    }

    // ===========================================================

    private ObjectMeta createDeploymentConfigMetaData(ResourceConfig config) {
        return new ObjectMetaBuilder()
                .withName(KubernetesHelper.validateKubernetesId(config.getControllerName(), "controller name"))
                .build();
    }

    private DeploymentConfigSpec createDeploymentConfigSpec(ResourceConfig config, List<ImageConfiguration> images, Long openshiftDeployTimeoutSeconds, RuntimeMode runtimeMode, Boolean enableAutomaticTrigger) {
        DeploymentConfigSpecBuilder specBuilder = new DeploymentConfigSpecBuilder();

        PodTemplateSpec podTemplateSpec = podTemplateHandler.getPodTemplate(config,images);
        PodSpec podSpec = podTemplateSpec.getSpec();

        specBuilder.withReplicas(config.getReplicas())
                .withTemplate(podTemplateSpec)
                .addNewTrigger().withType("ConfigChange").endTrigger();

        Map<String, String> containerToImageMap = new HashMap<>();
        Objects.requireNonNull(podSpec, "No PodSpec for PodTemplate:" + podTemplateSpec);
        List<Container> containers = podSpec.getContainers();
        Objects.requireNonNull(podSpec, "No containers for PodTemplate.spec: " + podTemplateSpec);
        for (Container container : containers) {
            validateContainer(container);
            containerToImageMap.put(container.getName(), container.getImage());
        }

        // add a new image change trigger for the build stream
        if (containerToImageMap.size() != 0 && runtimeMode == RuntimeMode.openshift) {
            for (Map.Entry<String, String> entry : containerToImageMap.entrySet()) {
                String containerName = entry.getKey();
                ImageName image = new ImageName(entry.getValue());
                String tag = image.getTag() != null ? image.getTag() : "latest";
                specBuilder.addNewTrigger()
                        .withType("ImageChange")
                        .withNewImageChangeParams()
                        .withAutomatic(enableAutomaticTrigger)
                        .withNewFrom()
                        .withKind("ImageStreamTag")
                        .withName(image.getSimpleName() + ":" + tag)
                        .withNamespace(image.getUser())
                        .endFrom()
                        .withContainerNames(containerName)
                        .endImageChangeParams()
                        .endTrigger();
            }
        }

        if (openshiftDeployTimeoutSeconds != null && openshiftDeployTimeoutSeconds > 0) {
            specBuilder.withNewStrategy().withType("Rolling").
                    withNewRollingParams().withTimeoutSeconds(openshiftDeployTimeoutSeconds).endRollingParams().endStrategy();
        }

        return specBuilder.build();
    }

    private void validateContainer(Container container) {
        if (container.getImage() == null) {
            throw new IllegalArgumentException("Container " + container.getName() + " has no Docker image configured. " +
                    "Please check your Docker image configuration (including the generators which are supposed to run)");
        }
    }
}