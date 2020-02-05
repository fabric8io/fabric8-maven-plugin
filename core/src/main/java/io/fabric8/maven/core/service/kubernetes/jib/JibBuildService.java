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
package io.fabric8.maven.core.service.kubernetes.jib;

import com.google.cloud.tools.jib.api.RegistryImage;
import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.core.service.Fabric8ServiceException;
import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.DeepCopy;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component(role = JibBuildService.class)
public class JibBuildService implements BuildService {

    @Requirement
    JibAssemblyManager jibAssemblyManager;

    private BuildServiceConfig config;

    private Logger log;

    public JibBuildService(BuildServiceConfig config, JibAssemblyManager jibAssemblyManager, Logger log) {
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

        public void setEnvMap(Map<String, String> envMap) {
            this.envMap = envMap;
        }

        public void setLabels(Map<String, String> labels) {
            this.labels = labels;
        }

        public void setVolumes(List<String> volumes) {
            this.volumes = volumes;
        }

        public void setPorts(List<String> ports) {
            this.ports = ports;
        }

        public void setFrom(RegistryImage from) {
            this.from = from;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public void setEntrypoint(Arguments entrypoint) {
            this.entrypoint = entrypoint;
        }

        public void setOutputDir(String outputDir) {
            this.outputDir = outputDir;
        }

        public void setWorkDir(String workDir) {
            this.workDir = workDir;
        }

        public void setCmd(Arguments cmd) {
            this.cmd = cmd;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public void setAssemblyConfiguration(AssemblyConfiguration assemblyConfiguration) {
            this.assemblyConfiguration = assemblyConfiguration;
        }

        public void setMojoParameters(MojoParameters mojoParameters) {
            this.mojoParameters = mojoParameters;
        }

        JibBuildConfiguration() { }

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
    }
}
