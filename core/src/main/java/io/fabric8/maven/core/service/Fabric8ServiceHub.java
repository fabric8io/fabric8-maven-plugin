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
package io.fabric8.maven.core.service;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.maven.core.access.ClusterAccess;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.service.kubernetes.DockerBuildService;
import io.fabric8.maven.core.service.openshift.OpenshiftBuildService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.openshift.client.OpenShiftClient;

/**
 * @author nicola
 * @since 17/02/2017
 */
public class Fabric8ServiceHub {

    private BuildService buildService;

    public Fabric8ServiceHub(ClusterAccess clusterAccess, PlatformMode mode, Logger log, ServiceHub dockerServiceHub) {
        PlatformMode resolvedMode = clusterAccess.resolvePlatformMode(mode, log);
        KubernetesClient client = clusterAccess.createDefaultClient(log);

        // Creating platform-dependent services
        if (resolvedMode == PlatformMode.kubernetes) {
            // Kubernetes services
            this.buildService = new DockerBuildService(dockerServiceHub);

        } else if(resolvedMode == PlatformMode.openshift) {
            // Openshift services
            this.buildService = new OpenshiftBuildService((OpenShiftClient) client, log, dockerServiceHub);

        } else {
            throw new IllegalArgumentException("Unknown platform mode " + mode + " resolved as "+ resolvedMode);
        }

    }

    public BuildService getBuildService() {
        return buildService;
    }

}
