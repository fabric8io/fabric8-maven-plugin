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
package io.fabric8.maven.core.service.kubernetes;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.core.service.Fabric8ServiceException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.Logger;

/**
 * @author nicola
 * @since 17/02/2017
 */
public class KubernetesBuildService implements BuildService {

    private final KubernetesClient client;
    private final Logger log;
    private ServiceHub dockerServiceHub;

    public KubernetesBuildService(KubernetesClient client, Logger log, ServiceHub dockerServiceHub) {
        this.client = client;
        this.log = log;
        this.dockerServiceHub = dockerServiceHub;
    }

    @Override
    public void build(BuildServiceConfig config, ImageConfiguration imageConfig) throws Fabric8ServiceException {

        io.fabric8.maven.docker.service.BuildService dockerBuildService = dockerServiceHub.getBuildService();
        io.fabric8.maven.docker.service.BuildService.BuildContext dockerBuildContext = config.getDockerBuildContext();
        try {
            dockerBuildService.buildImage(imageConfig, dockerBuildContext);

            // Assume we always want to tag
            dockerBuildService.tagImage(imageConfig.getName(), imageConfig);
        } catch (Exception ex) {
            throw new Fabric8ServiceException("Error while trying to build the image", ex);
        }
    }

}
