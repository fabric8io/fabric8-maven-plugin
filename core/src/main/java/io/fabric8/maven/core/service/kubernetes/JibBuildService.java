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

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.ImageFormat;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.JibContainer;
import com.google.cloud.tools.jib.api.RegistryException;
import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.core.util.JibBuildServiceUtil;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.DeepCopy;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

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
           doJibBuild(JibBuildServiceUtil.getJibBuildConfiguration(config, imageConfiguration, log));
       } catch (Exception ex) {
           throw new UnsupportedOperationException(ex);
       }
    }

    @Override
    public void postProcess(BuildServiceConfig config) {

    }

    public JibContainer doJibBuild(JibBuildService.JibBuildConfiguration jibBuildConfiguration) throws InvalidImageReferenceException, RegistryException, ExecutionException {
        return JibBuildServiceUtil.buildImage(jibBuildConfiguration, log);
    }

    public JibContainer doJibBuild(JibBuildService.JibBuildConfiguration jibBuildConfiguration, boolean isOfflineMode) throws InvalidImageReferenceException, RegistryException, ExecutionException {
        return JibBuildServiceUtil.buildImage(jibBuildConfiguration, log, isOfflineMode);
    }

    public static class JibBuildConfiguration {
        private ImageConfiguration imageConfiguration;

        private ImageFormat imageFormat;

        private Credential credential;

        private Path fatJarPath;

        private String targetDir;

        private String outputDir;

        private MojoParameters mojoParameters;

        private JibBuildConfiguration() {}

        public ImageConfiguration getImageConfiguration() { return imageConfiguration; }

        public String getTargetDir() {
            return targetDir;
        }

        public String getOutputDir() {
            return outputDir;
        }

        public Credential getCredential() {
            return credential;
        }

        public Path getFatJar() {
            return fatJarPath;
        }

        public ImageFormat getImageFormat() {
            return imageFormat;
        }

        public MojoParameters getMojoParameters() { return mojoParameters; }

        public static class Builder {
            private final JibBuildConfiguration configutil;
            private final Logger logger;

            public Builder(Logger logger) {
                this(null, logger);
            }

            public Builder(JibBuildConfiguration that, Logger logger) {
                this.logger = logger;
                if (that == null) {
                    this.configutil = new JibBuildConfiguration();
                } else {
                    this.configutil = DeepCopy.copy(that);
                }
            }

            public Builder mojoParameters(MojoParameters mojoParameters) {
                configutil.mojoParameters = mojoParameters;
                return this;
            }

            public Builder imageConfiguration(ImageConfiguration imageConfiguration) {
                configutil.imageConfiguration = imageConfiguration;
                return this;
            }

            public Builder imageFormat(ImageFormat imageFormat) {
                configutil.imageFormat = imageFormat;
                return this;
            }

            public Builder credential(Credential credential) {
                configutil.credential = credential;
                return this;
            }

            public Builder buildDirectory(String buildDir) {
                configutil.fatJarPath = JibBuildServiceUtil.getFatJar(buildDir, logger);
                return this;
            }

            public Builder targetDir(String targetDir) {
                configutil.targetDir = targetDir;
                return this;
            }

            public Builder outputDir(String outputDir) {
                configutil.outputDir = outputDir;
                return this;
            }

            public JibBuildConfiguration build() {
                return configutil;
            }
        }
    }
}
