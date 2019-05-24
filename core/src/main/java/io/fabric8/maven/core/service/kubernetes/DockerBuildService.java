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
package io.fabric8.maven.core.service.kubernetes;

import java.util.Objects;

import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.core.service.Fabric8ServiceException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ImagePullManager;
import io.fabric8.maven.docker.service.ServiceHub;

/**
 * @author nicola
 * @since 17/02/2017
 */
public class DockerBuildService implements BuildService {

    private ServiceHub dockerServiceHub;

    private BuildServiceConfig config;

    public DockerBuildService(ServiceHub dockerServiceHub, BuildServiceConfig config) {
        Objects.requireNonNull(dockerServiceHub, "dockerServiceHub");
        Objects.requireNonNull(config, "config");

        this.dockerServiceHub = dockerServiceHub;
        this.config = config;
    }

    @Override
    public void build(ImageConfiguration imageConfig) throws Fabric8ServiceException {

        io.fabric8.maven.docker.service.BuildService dockerBuildService = dockerServiceHub.getBuildService();
        io.fabric8.maven.docker.service.BuildService.BuildContext dockerBuildContext = config.getDockerBuildContext();
        ImagePullManager imagePullManager = config.getImagePullManager();
        try {
            dockerBuildService.buildImage(imageConfig, imagePullManager, dockerBuildContext);

            // Assume we always want to tag
            dockerBuildService.tagImage(imageConfig.getName(), imageConfig);
        } catch (Exception ex) {
            throw new Fabric8ServiceException("Error while trying to build the image", ex);
        }
    }

    @Override
    public void postProcess(BuildServiceConfig config) {
        // No post processing required
    }

}
