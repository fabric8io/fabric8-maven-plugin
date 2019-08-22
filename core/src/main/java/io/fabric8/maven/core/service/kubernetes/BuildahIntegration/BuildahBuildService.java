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
package io.fabric8.maven.core.service.kubernetes.BuildahIntegration;

import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;

import java.util.List;
import java.util.Objects;

public class BuildahBuildService implements BuildService {

    private BuildServiceConfig config;

    private Logger log;

    private BuildahBuildService() {

    }

    public BuildahBuildService(BuildServiceConfig config, Logger log) {
        Objects.requireNonNull(config, "config");
        this.config = config;
        this.log = log;
    }

    @Override
    public void build(ImageConfiguration imageConfiguration) {
        try {
            BuildImageConfiguration buildImageConfiguration = imageConfiguration.getBuildConfiguration();
            List<String> tags = buildImageConfiguration.getTags();

            BuildahBuildConfiguration buildahBuildConfiguration;
            String fullName = "";
            if (tags.size() > 0) {
                for (String tag : tags) {
                    if (tag != null) {
                        fullName = new ImageName(imageConfiguration.getName(), tag).getFullName();
                    }
                }
            } else {
                fullName = new ImageName(imageConfiguration.getName(), null).getFullName();
            }
            log.debug("Image tagging succesfull!");
            buildahBuildConfiguration = BuildahBuildServiceUtil.getBuildahBuildConfiguration(config, buildImageConfiguration, fullName, log);
            BuildahBuildServiceUtil.buildImage(buildahBuildConfiguration, log);
        } catch (Exception ex) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void postProcess(BuildServiceConfig config) {

    }
}
