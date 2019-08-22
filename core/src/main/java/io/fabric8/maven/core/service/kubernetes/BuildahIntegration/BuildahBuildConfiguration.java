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

import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.util.DeepCopy;
import io.fabric8.maven.docker.util.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class BuildahBuildConfiguration {

    private AuthConfig authConfig;

    private String from;

    private String targetImage;

    private String targetDir;

    private List<String> ports;

    private Map<String, String> envMap;

    private Map<String, String> labelMap;

    private Path fatJarPath;

    private Arguments entrypoint;

    public Arguments getEntryPoint() {return entrypoint;}

    public String getTargetDir() {return targetDir;}

    public Map<String, String> getEnvMap() {
        return envMap;
    }

    public Map<String, String> getLabelMap() {
        return labelMap;
    }

    public List<String> getPorts() {return ports;}

    public String getFrom() {return from;}

    public String getTargetImage() {return targetImage;}

    public AuthConfig getAuthConfig() {
        return authConfig;
    }

    public Path getFatJar() {return fatJarPath;}

    public static class Builder {

        private final BuildahBuildConfiguration configutil;
        private final Logger logger;

        public Builder(Logger logger) {
            this(null, logger);
        }

        public Builder(BuildahBuildConfiguration that, Logger logger) {
            this.logger = logger;
            if (that == null) {
                this.configutil = new BuildahBuildConfiguration();
            } else {
                this.configutil = DeepCopy.copy(that);
            }
        }

        public Builder authConfig(AuthConfig authConfig) {
            configutil.authConfig = authConfig;
            return this;
        }

        public Builder from(String from) {
            configutil.from = from;
            return this;
        }

        public Builder targetImage(String targetImage) {
            configutil.targetImage = targetImage;
            return this;
        }

        public Builder targetDir(String targetDir) {
            configutil.targetDir = targetDir;
            return this;
        }

        public Builder ports(List<String> ports) {
            configutil.ports = ports;
            return this;
        }

        public Builder buildDirectory(String buildDir) {
            configutil.fatJarPath = BuildahBuildServiceUtil.getFatJar(buildDir, logger);
            return this;
        }

        public Builder envMap(Map<String, String> envMap) {
            configutil.envMap = envMap;
            return this;
        }

        public Builder labelMap(Map<String, String> labelMap) {
            configutil.labelMap = labelMap;
            return this;
        }

        public BuildahBuildConfiguration.Builder entrypoint(Arguments entrypoint) {
            configutil.entrypoint = entrypoint;
            return this;
        }

        public BuildahBuildConfiguration build() {
            return configutil;
        }
    }
}
