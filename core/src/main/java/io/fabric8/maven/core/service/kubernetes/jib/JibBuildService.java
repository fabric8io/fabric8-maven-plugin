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

import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.TarImage;
import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.core.service.Fabric8ServiceException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Objects;

import static io.fabric8.maven.core.service.kubernetes.jib.JibServiceUtil.addAssemblyFiles;
import static io.fabric8.maven.core.service.kubernetes.jib.JibServiceUtil.buildContainer;
import static io.fabric8.maven.core.service.kubernetes.jib.JibServiceUtil.containerFromImageConfiguration;
import static io.fabric8.maven.core.service.kubernetes.jib.JibServiceUtil.imageNameFromImageConfiguration;

public class JibBuildService implements BuildService {

    private static final String TAR_SUFFIX = ".tar";

    private JibAssemblyManager jibAssemblyManager;

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
           log.info("JIB image build started");
           final JibContainerBuilder containerBuilder = containerFromImageConfiguration(imageConfiguration);
           log.info("Preparing assembly files");
           final TarImage tarImage = prepareAssembly(imageConfiguration, containerBuilder);
           buildContainer(containerBuilder, tarImage, log);
           log.info(" %s successfully built", imageNameFromImageConfiguration(imageConfiguration));
       } catch (Exception ex) {
           throw new Fabric8ServiceException("Error when building JIB image", ex);
       }
    }

    private TarImage prepareAssembly(ImageConfiguration imageConfiguration, JibContainerBuilder containerBuilder)
      throws InvalidImageReferenceException, MojoExecutionException, IOException {

        final String targetImage = imageNameFromImageConfiguration(imageConfiguration);
        final MojoParameters mojoParameters = config.getDockerMojoParameters();
        final String outputDir = EnvUtil.prepareAbsoluteOutputDirPath(mojoParameters, "", "").getAbsolutePath();
        addAssemblyFiles(containerBuilder, jibAssemblyManager,
          imageConfiguration.getBuildConfiguration().getAssemblyConfiguration(),
          mojoParameters, targetImage, log);

        final String imageTarName = ImageReference.parse(targetImage).toString().concat(TAR_SUFFIX);
        log.info("Building Image Tarball at %s ...", imageTarName);
        return TarImage.at(Paths.get(outputDir, imageTarName)).named(targetImage);
    }

    @Override
    public void postProcess(BuildServiceConfig config) {

    }
}
