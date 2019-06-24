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
package io.fabric8.maven.core.util;

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.LayerConfiguration;
import com.google.cloud.tools.jib.api.Port;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.TarImage;
import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.core.service.kubernetes.JibBuildService;
import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.service.RegistryService;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Class with the static utility methods consumed by io.fabric8.maven.core.service.kubernetes.JibBuildService.
 */
public class JibBuildServiceUtil {

    private JibBuildServiceUtil() {}

    private static final String DEFAULT_JAR_NAME = "/app.jar";
    private static final String DEFAULT_USER_NAME = "fabric8/";

    /**
     * Builds a container image using JIB
     * @param buildConfiguration
     * @param log
     * @throws InvalidImageReferenceException
     */
    public static void buildImage(JibBuildService.JibBuildConfiguration buildConfiguration, Logger log) throws InvalidImageReferenceException {

        String fromImage = buildConfiguration.getFrom();
        String targetImage = buildConfiguration.getTargetImage();
        Credential credential = buildConfiguration.getCredential();
        Map<String, String> envMap  = buildConfiguration.getEnvMap();
        List<String> portList = buildConfiguration.getPorts();
        Set<Port> portSet = getPortSet(portList);
        String  outputDir = buildConfiguration.getOutputDir();
        String targetDir = buildConfiguration.getTargetDir();
        Path fatJar = buildConfiguration.getFatJar();

        List<String> entrypointList = new ArrayList<>();
        if(buildConfiguration.getEntryPoint() != null) {
            entrypointList = buildConfiguration.getEntryPoint().asStrings();
        }

        buildImage(fromImage, targetImage, envMap, credential, portSet, fatJar, entrypointList, targetDir, outputDir, log);
    }

    /**
     * Builds a container image using Jib from all the following parameters:
     *
     * @param baseImage
     * @param targetImage
     * @param envMap
     * @param credential
     * @param portSet
     * @param fatJar
     * @param entrypointList
     * @param targetDir
     * @param outputDir
     * @param log
     * @return
     * @throws InvalidImageReferenceException
     */
    protected static JibContainer buildImage(String baseImage, String targetImage, Map<String, String> envMap, Credential credential, Set<Port> portSet, Path fatJar, List<String> entrypointList, String targetDir, String outputDir, Logger log) throws InvalidImageReferenceException {
        String username = "";
        String password = "";

        JibContainerBuilder contBuild = Jib.from(baseImage);

        if (envMap != null) {
            contBuild = contBuild.setEnvironment(envMap);
        }

        if (portSet != null) {
            contBuild = contBuild.setExposedPorts(portSet);
        }

        if (fatJar != null) {
            String fatJarName = fatJar.getFileName().toString();
            String jarPath = targetDir + "/" + (fatJarName.isEmpty() ? DEFAULT_JAR_NAME: fatJarName);
            contBuild = contBuild
                    .addLayer(LayerConfiguration.builder().addEntry(fatJar, AbsoluteUnixPath.get(jarPath)).build());
        }

        if(!entrypointList.isEmpty()) {
            contBuild = contBuild.setEntrypoint(entrypointList);
        }

        if (credential != null) {
            username = credential.getUsername();
            password = credential.getPassword();

            if (targetImage.contains(DEFAULT_USER_NAME)) {
                targetImage = targetImage.replaceFirst(DEFAULT_USER_NAME, username + "/");
            }
        }

        RegistryImage registryImage = RegistryImage.named(targetImage).addCredential(username, password);
        String imageTarName = ImageReference.parse(targetImage).getRepository().concat(".tar");
        TarImage tarImage = TarImage.named(targetImage).saveTo(Paths.get(outputDir + "/" + imageTarName));
        try {
            JibContainer jibContainer = buildContainer(contBuild, registryImage, log);
            log.info("Image %s successfully built and pushed.", targetImage);
            return jibContainer;
        } catch (RegistryException re) {
            log.warn("Registry Exception occureed : %s", re.getMessage());
            log.warn("Credentials are probably either not configured or are incorrect.");
            log.info("Building Image Tarball at %s.", imageTarName);
            JibContainer jibContainer =  buildContainer(contBuild, tarImage, log, false);
            log.info(" %s successfully built.", Paths.get(outputDir + "/" + imageTarName));
            return jibContainer;
        } catch (ExecutionException e) {
            log.warn("Can't connect to the remote registry host: %s", e.getMessage());
            JibContainer jibContainer =  buildContainer(contBuild, tarImage, log, true);
            log.info("%s successfully built.", Paths.get(outputDir + "/" + imageTarName));
            return jibContainer;
        }
    }

    public static JibContainer buildContainer(JibContainerBuilder jibContainerBuilder, TarImage image, Logger logger, boolean offline) {
        try {
            if (offline) {
                logger.info("Trying to build the image tarball in the offline mode.");
            }
            return jibContainerBuilder.containerize(Containerizer.to(image).setOfflineMode(offline));
        } catch (CacheDirectoryCreationException | IOException | InterruptedException | ExecutionException | RegistryException ex) {
            logger.error("Unable to build the image tarball: %s", ex.getMessage());
            throw new IllegalStateException(ex);
        }
    }
    public static JibContainer buildContainer(JibContainerBuilder jibContainerBuilder, RegistryImage image,Logger logger) throws RegistryException, ExecutionException {
        try {
            return jibContainerBuilder.containerize(Containerizer.to(image));
        } catch (CacheDirectoryCreationException | IOException | InterruptedException e) {
            logger.error("Unable to build the image in the offline mode: %s", e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    public static JibBuildService.JibBuildConfiguration getJibBuildConfiguration(BuildService.BuildServiceConfig config, BuildImageConfiguration buildImageConfiguration, String fullImageName, Logger log) throws MojoExecutionException {

        io.fabric8.maven.docker.service.BuildService.BuildContext dockerBuildContext = config.getDockerBuildContext();
        RegistryService.RegistryConfig registryConfig = dockerBuildContext.getRegistryConfig();

        String targetDir = buildImageConfiguration.getAssemblyConfiguration().getTargetDir();

        MojoParameters mojoParameters = config.getDockerMojoParameters();
        String outputDir = mojoParameters.getOutputDirectory();

        if(targetDir == null) {
            targetDir = "/deployments";
        }

        AuthConfig authConfig = registryConfig.getAuthConfigFactory()
                .createAuthConfig(true, true, registryConfig.getAuthConfig(),
                        registryConfig.getSettings(), null, registryConfig.getRegistry());

        JibBuildService.JibBuildConfiguration.Builder jibBuildConfigurationBuilder = new JibBuildService.JibBuildConfiguration.Builder(log).from(buildImageConfiguration.getFrom())
                                                                                                        .envMap(buildImageConfiguration.getEnv())
                                                                                                        .ports(buildImageConfiguration.getPorts())
                                                                                                        .entrypoint(buildImageConfiguration.getEntryPoint())
                                                                                                        .targetImage(fullImageName)
                                                                                                        .targetDir(targetDir)
                                                                                                        .outputDir(outputDir)
                                                                                                        .buildDirectory(config.getBuildDirectory());
        if(authConfig != null) {
            jibBuildConfigurationBuilder.credential(Credential.from(authConfig.getUsername(), authConfig.getPassword()));
        }

        return jibBuildConfigurationBuilder.build();
    }


    private static Set<Port> getPortSet(List<String> ports) {

        Set<Port> portSet = new HashSet<Port>();
        for(String port : ports) {
            portSet.add(Port.tcp(Integer.parseInt(port)));
        }

        return portSet;
    }

    public static Path getFatJar(String buildDir, Logger log) {
        FatJarDetector fatJarDetector = new FatJarDetector(buildDir);
        try {
            FatJarDetector.Result result = fatJarDetector.scan();
            if(result != null) {
                return result.getArchiveFile().toPath();
            }

        } catch (MojoExecutionException e) {
            log.error("MOJO Execution exception occurred: %s", e);
            throw new UnsupportedOperationException();
        }
        return null;
    }
}