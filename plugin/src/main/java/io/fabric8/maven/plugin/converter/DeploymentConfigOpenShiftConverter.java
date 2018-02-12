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

package io.fabric8.maven.plugin.converter;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import io.fabric8.openshift.api.model.DeploymentConfigFluent;

/**
 * Takes a DeploymentConfig generated from a vanilla Deployment as part of the aggregation
 * and enriches it with the timeout.
 *
 * See discussions about the timeout : https://github.com/openshift/origin/issues/10531
 *
 * -- Discussion --------------------------------------------------------------------------------------------
 * roland: in fact, this is the wrong place for adapting an OpenShift object (or 'converting an OpenShift object
 * to an OpenShift object'), as the converters only purpose should be to *convert* Kubernetes to OpenShift objects.
 * This here looks like it should go into an enricher. I know, that the enriching phase happens before the
 * conversion phase, so deployment configs which were created on behalf of a conversion from
 * Deployment -> DeploymentConfig wont be enriched anymore.
 *
 * There are two solutions to get rid of this converter:
 *
 * * Add an enricher for regular DeploymentConfigs provided by an user to add this timeout. Drawback: Timeout would
 *   be needed to be configured twice
 * * Add a second enrich phase after conversion to OpenShift objects.
 * ------------------------------------------------------------------------------------------------------------
 */
public class DeploymentConfigOpenShiftConverter implements KubernetesToOpenShiftConverter {
    private final Long openshiftDeployTimeoutSeconds;

    public DeploymentConfigOpenShiftConverter(Long openshiftDeployTimeoutSeconds) {
        this.openshiftDeployTimeoutSeconds = openshiftDeployTimeoutSeconds;
    }

    @Override
    public HasMetadata convert(HasMetadata item, boolean trimImageInContainerSpec, boolean enableAutomaticTrigger) {
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
