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

import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.core.config.BuildRecreateMode;
import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.MojoParameters;
import io.fabric8.maven.docker.util.Task;

/**
 * @author nicola
 * @since 17/02/2017
 */
public interface BuildService {

    /**
     * Builds the given image using the specified configuration.
     *
     * @param imageConfig the image to build
     */
    void build(ImageConfiguration imageConfig) throws Fabric8ServiceException;


    /**
     * Class to hold configuration parameters for the building service.
     */
    class BuildServiceConfig {

        private io.fabric8.maven.docker.service.BuildService.BuildContext dockerBuildContext;

        private MojoParameters dockerMojoParameters;

        private BuildRecreateMode buildRecreateMode;

        private OpenShiftBuildStrategy openshiftBuildStrategy;

        private String s2iBuildNameSuffix;

        private Task<KubernetesListBuilder> enricherTask;

        private String buildDirectory;

        public BuildServiceConfig() {
        }

        public io.fabric8.maven.docker.service.BuildService.BuildContext getDockerBuildContext() {
            return dockerBuildContext;
        }

        public MojoParameters getDockerMojoParameters() {
            return dockerMojoParameters;
        }

        public BuildRecreateMode getBuildRecreateMode() {
            return buildRecreateMode;
        }

        public OpenShiftBuildStrategy getOpenshiftBuildStrategy() {
            return openshiftBuildStrategy;
        }

        public String getS2iBuildNameSuffix() {
            return s2iBuildNameSuffix;
        }

        public Task<KubernetesListBuilder> getEnricherTask() {
            return enricherTask;
        }

        public String getBuildDirectory() {
            return buildDirectory;
        }

        public static class Builder {
            private BuildServiceConfig config;

            public Builder() {
                this.config = new BuildServiceConfig();
            }

            public Builder(BuildServiceConfig config) {
                this.config = config;
            }

            public Builder dockerBuildContext(io.fabric8.maven.docker.service.BuildService.BuildContext dockerBuildContext) {
                config.dockerBuildContext = dockerBuildContext;
                return this;
            }

            public Builder dockerMojoParameters(MojoParameters dockerMojoParameters) {
                config.dockerMojoParameters = dockerMojoParameters;
                return this;
            }

            public Builder buildRecreateMode(BuildRecreateMode buildRecreateMode) {
                config.buildRecreateMode = buildRecreateMode;
                return this;
            }

            public Builder openshiftBuildStrategy(OpenShiftBuildStrategy openshiftBuildStrategy) {
                config.openshiftBuildStrategy = openshiftBuildStrategy;
                return this;
            }

            public Builder s2iBuildNameSuffix(String s2iBuildNameSuffix) {
                config.s2iBuildNameSuffix = s2iBuildNameSuffix;
                return this;
            }

            public Builder enricherTask(Task<KubernetesListBuilder> enricherTask) {
                config.enricherTask = enricherTask;
                return this;
            }

            public Builder buildDirectory(String buildDirectory) {
                config.buildDirectory = buildDirectory;
                return this;
            }

            public BuildServiceConfig build() {
                return config;
            }

        }

    }


}
