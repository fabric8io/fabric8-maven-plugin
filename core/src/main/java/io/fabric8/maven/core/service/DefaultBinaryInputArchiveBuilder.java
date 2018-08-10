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

package io.fabric8.maven.core.service;

import io.fabric8.maven.core.util.IoUtil;
import io.fabric8.maven.docker.assembly.ArchiverCustomizer;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.archiver.tar.TarArchiver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

public class DefaultBinaryInputArchiveBuilder implements BinaryInputArchiveBuilder {

    private BuildService.BuildServiceConfig config;
    private File binaryInputTar = null;

    public DefaultBinaryInputArchiveBuilder(BuildService.BuildServiceConfig config) {
        this.config = config;
    }

    @Override
    public void createBinaryInput(ServiceHub hub, File targetDir, ImageConfiguration imageConfiguration) throws Fabric8ServiceException {
        // Adding S2I artifacts such as environment variables in S2I mode
        ArchiverCustomizer customizer = getS2ICustomizer(imageConfiguration);

        try {
            // Create tar file with Docker archive
            if (customizer != null) {
                this.binaryInputTar = hub.getArchiveService().createDockerBuildArchive(imageConfiguration, config.getDockerMojoParameters(), customizer);
            } else {
                this.binaryInputTar = hub.getArchiveService().createDockerBuildArchive(imageConfiguration, config.getDockerMojoParameters());
            }
        } catch (MojoExecutionException e) {
            throw new Fabric8ServiceException("Unable to create the build archive", e);
        }
    }

    private ArchiverCustomizer getS2ICustomizer(ImageConfiguration imageConfiguration) throws Fabric8ServiceException {
        try {
            if (imageConfiguration.getBuildConfiguration() != null && imageConfiguration.getBuildConfiguration().getEnv() != null) {
                String fileName = IoUtil.sanitizeFileName("s2i-env-" + imageConfiguration.getName());
                final File environmentFile = new File(config.getBuildDirectory(), fileName);

                try (PrintWriter out = new PrintWriter(new FileWriter(environmentFile))) {
                    for (Map.Entry<String, String> e : imageConfiguration.getBuildConfiguration().getEnv().entrySet()) {
                        out.println(e.getKey() + "=" + e.getValue());
                    }
                }

                return new ArchiverCustomizer() {
                    @Override
                    public TarArchiver customize(TarArchiver tarArchiver) throws IOException {
                        tarArchiver.addFile(environmentFile, ".s2i/environment");
                        return tarArchiver;
                    }
                };
            } else {
                return null;
            }
        } catch (IOException e) {
            throw new Fabric8ServiceException("Unable to add environment variables to the S2I build archive", e);
        }
    }

    public File getBinaryInputTar() {
        return binaryInputTar;
    }
}
