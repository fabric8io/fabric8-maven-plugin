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

import com.google.cloud.tools.jib.api.RegistryImage;
import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.core.util.JibServiceUtil;
import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.DeepCopy;
import io.fabric8.maven.docker.util.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JibBuildService implements BuildService {

    private BuildServiceConfig config;

    private Logger log;
    private JibBuildService() { }

    public JibBuildService (BuildServiceConfig config, Logger log) {
        Objects.requireNonNull(config, "config");
        this.config = config;
        this.log = log;
    }

    @Override
    public void build(ImageConfiguration imageConfiguration) {
       try {
           JibBuildService.JibBuildConfiguration jibBuildConfiguration = JibServiceUtil.getJibBuildConfiguration(config, imageConfiguration, log);
           JibServiceUtil.buildImage(jibBuildConfiguration, log);
       } catch (Exception ex) {
           throw new UnsupportedOperationException(ex);
       }
    }

    @Override
    public void postProcess(BuildServiceConfig config) {

    }

    @Override
    public BuildServiceConfig getBuildServiceConfig() {
        return config;
    }

    public static class JibBuildConfiguration {

        private Map<String, String> envMap;

        private Map<String, String> labels;

        private List<String> volumes;

        private List<String> ports;

        private RegistryImage from;

        private String target;

        private Path fatJarPath;

        private Arguments entrypoint;

        private String targetDir;

        private String outputDir;

        private String workDir;

        private JibBuildConfiguration() {}

        public Arguments getEntryPoint() {
            return entrypoint;
        }

        public String getTargetDir() {
            return targetDir;
        }

        public String getOutputDir() {
            return outputDir;
        }

        public Map<String, String> getEnvMap() {
            return envMap;
        }

        public Map<String, String> getLabels() { return labels; }

        public List<String> getVolumes() {  return volumes; }

        public List<String> getPorts() {
            return ports;
        }

        public RegistryImage getFrom() {
            return from;
        }

        public String getTargetImage() {
            return target;
        }

        public Path getFatJar() {
            return fatJarPath;
        }

        public String getWorkDir() { return workDir; }

        public static class Builder {
            private final JibBuildConfiguration jibBuildConfiguration;
            private final Logger logger;

            public Builder(Logger logger) {
                this(null, logger);
            }

            public Builder(JibBuildConfiguration that, Logger logger) {
                this.logger = logger;
                if (that == null) {
                    this.jibBuildConfiguration = new JibBuildConfiguration();
                } else {
                    this.jibBuildConfiguration = DeepCopy.copy(that);
                }
            }

            public Builder envMap(Map<String, String> envMap) {
                jibBuildConfiguration.envMap = envMap;
                return this;
            }

            public Builder labels(Map<String, String> labels) {
                jibBuildConfiguration.labels = labels;
                return this;
            }

            public Builder volumes(List<String> volumes) {
                jibBuildConfiguration.volumes = volumes;
                return this;
            }
            public Builder ports(List<String> ports) {
                jibBuildConfiguration.ports = ports;
                return this;
            }

            public Builder from(RegistryImage from) {
                jibBuildConfiguration.from = from;
                return this;
            }

            public Builder targetImage(String imageName) {
                jibBuildConfiguration.target = imageName;
                return this;
            }

            public Builder entrypoint(Arguments entrypoint) {
                jibBuildConfiguration.entrypoint = entrypoint;
                return this;
            }

            public Builder buildDirectory(String buildDir) {
                jibBuildConfiguration.fatJarPath = JibServiceUtil.getFatJar(buildDir, logger);
                return this;
            }

            public Builder workingDirectory(String workDir) {
                jibBuildConfiguration.workDir = workDir;
                return this;
            }

            public Builder targetDir(String targetDir) {
                jibBuildConfiguration.targetDir = targetDir;
                return this;
            }

            public Builder outputDir(String outputDir) {
                jibBuildConfiguration.outputDir = outputDir;
                return this;
            }

            public JibBuildConfiguration build() {
                return jibBuildConfiguration;
            }
        }
    }
}
