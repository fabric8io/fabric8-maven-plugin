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

import io.fabric8.maven.core.util.ResourceUtil;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class WarBinaryInputArchiveBuilder implements BinaryInputArchiveBuilder {
    private BuildService.BuildServiceConfig config;
    private File binaryInputTar = null;

    public WarBinaryInputArchiveBuilder(BuildService.BuildServiceConfig config) {
        this.config = config;
    }

    @Override
    public void createBinaryInput(ServiceHub hub, File binaryInputDirectory, ImageConfiguration imageConfiguration) throws Fabric8ServiceException {
        try {
            File targetDirectory = new File(config.getBuildDirectory());
            String[] jarOrWars = ResourceUtil.getFileListOfExtension(targetDirectory, ".war");

            if(jarOrWars == null || jarOrWars.length == 0) {
                throw new MojoExecutionException("No war/jar built yet. Please ensure that the 'package' phase has run");
            }

            File warFileLocation = ResourceUtil.getArchiveFile(targetDirectory, jarOrWars);
            File targetWarFileLocation = new File(binaryInputDirectory, warFileLocation.getName());
            targetWarFileLocation.createNewFile();
            Files.copy(Paths.get(warFileLocation.getAbsolutePath()), Paths.get(targetWarFileLocation.getAbsolutePath()), StandardCopyOption.REPLACE_EXISTING);
        } catch(IOException exception) {
            throw new Fabric8ServiceException("Cannot examine "+ binaryInputDirectory.getName() + " for the manifest");
        } catch (MojoExecutionException mojoException) {
            throw new Fabric8ServiceException(mojoException);
        }
    }

    @Override
    public File getBinaryInputTar() { return binaryInputTar; }
}
