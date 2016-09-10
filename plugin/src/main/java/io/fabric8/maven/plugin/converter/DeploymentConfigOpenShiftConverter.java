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

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import io.fabric8.openshift.api.model.DeploymentConfigFluent;

/**
 * Takes a DeploymentConfig generated from a vanilla Deployment as part of the aggregation
 * and enriches it with the timeout
 */
public class DeploymentConfigOpenShiftConverter implements KubernetesToOpenShiftConverter {
    private final Long openshiftDeployTimeoutSeconds;

    public DeploymentConfigOpenShiftConverter(Long openshiftDeployTimeoutSeconds) {
        this.openshiftDeployTimeoutSeconds = openshiftDeployTimeoutSeconds;
    }

    @Override
    public HasMetadata convert(HasMetadata item) {
        if (item instanceof DeploymentConfig) {
            DeploymentConfig resource = (DeploymentConfig) item;

            if (openshiftDeployTimeoutSeconds != null && openshiftDeployTimeoutSeconds > 0) {
                DeploymentConfigBuilder builder = new DeploymentConfigBuilder(resource);
                DeploymentConfigFluent.SpecNested<DeploymentConfigBuilder> specBuilder;
                if (resource.getSpec() != null) {
                    specBuilder = builder.editSpec();
                } else {
                    specBuilder = builder.withNewSpec();
                }
                specBuilder.withNewStrategy().withType("Rolling").
                        withNewRollingParams().withTimeoutSeconds(openshiftDeployTimeoutSeconds).endRollingParams().endStrategy();
                specBuilder.endSpec();
                return builder.build();
            }
        }
        return item;
    }
}
