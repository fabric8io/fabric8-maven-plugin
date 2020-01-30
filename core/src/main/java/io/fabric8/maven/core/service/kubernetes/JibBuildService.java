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
import io.fabric8.maven.core.service.Fabric8ServiceException;
import io.fabric8.maven.core.util.JibAssemblyManager;
import io.fabric8.maven.core.util.JibServiceUtil;
import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.DeepCopy;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component(role = JibBuildService.class)
public class JibBuildService implements BuildService {

    @Requirement
    JibAssemblyManager jibAssemblyManager;

    private BuildServiceConfig config;

    private Logger log;

    public JibBuildService (BuildServiceConfig config, JibAssemblyManager jibAssemblyManager, Logger log) {
        Objects.requireNonNull(config, "config");
        this.config = config;
        this.log = log;
        this.jibAssemblyManager = jibAssemblyManager;
    }

    @Override
    public void build(ImageConfiguration imageConfiguration) throws Fabric8ServiceException {
       try {
           JibBuildService.JibBuildConfiguration jibBuildConfiguration = JibServiceUtil.getJibBuildConfiguration(config, imageConfiguration, log);
           JibServiceUtil.buildImage(jibBuildConfiguration, jibAssemblyManager, log);
       } catch (Exception ex) {
           throw new UnsupportedOperationException(ex);
       }
    }

    @Override
    public void postProcess(BuildServiceConfig config) {

    }

    public static class JibBuildConfiguration {

        private Map<String, String> envMap;

        private Map<String, String> labels;

        private List<String> volumes;

        private List<String> ports;

        private RegistryImage from;

        private String target;

        private Arguments entrypoint;

        private String outputDir;

        private String workDir;

        private Arguments cmd;

        private String user;

        private AssemblyConfiguration assemblyConfiguration;

        private MojoParameters mojoParameters;

        private JibBuildConfiguration() {}

        public Arguments getEntryPoint() {
            return entrypoint;
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

        public String getWorkDir() { return workDir; }

        public String getUser() {   return user; }

        public Arguments getCmd() { return cmd; }

        public AssemblyConfiguration getAssemblyConfiguration() {
            return assemblyConfiguration;
        }

        public MojoParameters getMojoParameters() {
            return mojoParameters;
        }

        public static class Builder {
            private final JibBuildConfiguration jibBuildConfiguration;

            public Builder() {
                this(null);
            }

            public Builder(JibBuildConfiguration that) {
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

            public Builder workingDirectory(String workDir) {
                jibBuildConfiguration.workDir = workDir;
                return this;
            }

            public Builder outputDir(String outputDir) {
                jibBuildConfiguration.outputDir = outputDir;
                return this;
            }

            public Builder assemblyConfiguration(AssemblyConfiguration assemblyConfiguration) {
                jibBuildConfiguration.assemblyConfiguration = assemblyConfiguration;
                return this;
            }

            public Builder mojoParameters(MojoParameters mojoParameters) {
                jibBuildConfiguration.mojoParameters = mojoParameters;
                return this;
            }

            public Builder user(String user) {
                jibBuildConfiguration.user = user;
                return this;
            }

            public Builder cmd(Arguments cmd) {
                jibBuildConfiguration.cmd = cmd;
                return this;
            }

            public JibBuildConfiguration build() {
                return jibBuildConfiguration;
            }
        }
    }
}
