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

package io.fabric8.maven.core.handler;

import java.util.List;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpec;
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpecBuilder;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.util.kubernetes.KubernetesHelper;
import io.fabric8.maven.docker.config.ImageConfiguration;

/**
 */
public class DeploymentHandler {
    private final PodTemplateHandler podTemplateHandler;

    DeploymentHandler(PodTemplateHandler podTemplateHandler) {
        this.podTemplateHandler = podTemplateHandler;
    }

    public Deployment getDeployment(ResourceConfig config,
                                    List<ImageConfiguration> images) {
        Deployment deployment = new DeploymentBuilder()
            .withMetadata(createDeploymentMetaData(config))
            .withSpec(createDeploymentSpec(config, images))
            .build();

        return deployment;
    }

    // ===========================================================

    private ObjectMeta createDeploymentMetaData(ResourceConfig config) {
        return new ObjectMetaBuilder()
            .withName(KubernetesHelper.validateKubernetesId(config.getControllerName(), "controller name"))
            .build();
    }

    private DeploymentSpec createDeploymentSpec(ResourceConfig config, List<ImageConfiguration> images) {
        return new DeploymentSpecBuilder()
            .withReplicas(config.getReplicas())
            .withTemplate(podTemplateHandler.getPodTemplate(config,images))
            .build();
    }
}
