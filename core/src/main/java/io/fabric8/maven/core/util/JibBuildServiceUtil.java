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
import com.google.cloud.tools.jib.api.ImageFormat;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.LayerConfiguration;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.Port;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.TarImage;
import com.google.cloud.tools.jib.event.events.ProgressEvent;
import com.google.cloud.tools.jib.event.events.TimerEvent;
import com.google.cloud.tools.jib.event.progress.ProgressEventHandler;
import com.google.cloud.tools.jib.plugins.common.TimerEventHandler;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLoggerBuilder;
import com.google.cloud.tools.jib.plugins.common.logging.ProgressDisplayGenerator;
import com.google.cloud.tools.jib.plugins.common.logging.SingleThreadedExecutor;
import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.core.service.kubernetes.JibBuildService;
import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.RegistryService;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Class with the static utility methods consumed by io.fabric8.maven.core.service.kubernetes.JibBuildService.
 */
public class JibBuildServiceUtil {

    private JibBuildServiceUtil() {}

    private static final String DEFAULT_JAR_NAME = "/app.jar";
    private static final String DEFAULT_USER_NAME = "fabric8/";
    public static final String FABRIC8_GENERATOR_NAME = "fabric8.generator.name";
    public static final String FABRIC8_GENERATOR_REGISTRY = "fabric8.generator.registry";
    public static final String FABRIC8_GENERATOR_FROM = "fabric8.generator.from";
    private static ConsoleLogger consoleLogger;

    /**
     * Builds a container image using JIB
     * @param buildConfiguration Jib build configuration
     * @param log logger object
     * @throws InvalidImageReferenceException
     */
    public static JibContainer buildImage(JibBuildService.JibBuildConfiguration buildConfiguration, Logger log) throws InvalidImageReferenceException, RegistryException, ExecutionException {
        return buildImage(buildConfiguration, log, false);
    }

    public static JibContainer buildImage(JibBuildService.JibBuildConfiguration buildConfiguration, Logger log, boolean isOfflineMode) throws InvalidImageReferenceException, RegistryException, ExecutionException {
        ImageConfiguration imageConfiguration = buildConfiguration.getImageConfiguration();
        Credential credential = buildConfiguration.getCredential();
        String outputDir = buildConfiguration.getOutputDir();
        String targetDir = buildConfiguration.getTargetDir();
        Path fatJar = buildConfiguration.getFatJar();
        ImageFormat imageFormat = buildConfiguration.getImageFormat() != null ? buildConfiguration.getImageFormat() : ImageFormat.Docker;

        return buildImage(imageConfiguration, buildConfiguration.getMojoParameters(), imageFormat, credential, fatJar, targetDir, outputDir, log, isOfflineMode);
    }

    /**
     * Builds a container image using Jib from all the following parameters:
     *
     * @param imageConfiguration Image Configuration
     * @param mojoParameters Mojo parameters
     * @param credential login credentials
     * @param fatJar path to fat jar
     * @param targetDir target directory
     * @param outputDir output directory
     * @param log log object
     * @param isOfflineMode whether to build in offline mode or not.
     * @return returns jib container
     *
     * @throws InvalidImageReferenceException
     */
    protected static JibContainer buildImage(ImageConfiguration imageConfiguration, MojoParameters mojoParameters, ImageFormat imageFormat, Credential credential, Path fatJar, String targetDir, String outputDir, Logger log, boolean isOfflineMode) throws InvalidImageReferenceException, RegistryException, ExecutionException {
        String targetImage = getPropertyFromMojoParameter(mojoParameters, FABRIC8_GENERATOR_NAME) != null ?
                getPropertyFromMojoParameter(mojoParameters, FABRIC8_GENERATOR_NAME) : imageConfiguration.getName();

        JibContainerBuilder containerBuilder = getContainerBuilderFromImageConfiguration(imageConfiguration, mojoParameters);
        containerBuilder = getJibContainerBuilderFromFatJarPath(fatJar, targetDir, containerBuilder);
        containerBuilder.setFormat(imageFormat);

        String imageTarName = ImageReference.parse(targetImage).getRepository().concat(".tar");
        TarImage tarImage = TarImage.named(targetImage).saveTo(Paths.get(outputDir + "/" + imageTarName));
        RegistryImage registryImage = getRegistryImage(imageConfiguration, mojoParameters, credential);

        try {
            JibContainer jibContainer;
            if (Boolean.FALSE.equals(isOfflineMode)) {
                jibContainer = buildContainer(containerBuilder, registryImage, log);
            } else {
                jibContainer = buildContainer(containerBuilder, tarImage, log, isOfflineMode);
            }
            log.info("Image %s successfully built and pushed.", targetImage);
            return jibContainer;
        } catch (RegistryException re) {
            log.info("Building Image Tarball at %s.", imageTarName);
            buildContainer(containerBuilder, tarImage, log, false);
            log.info(" %s successfully built.", Paths.get(outputDir + "/" + imageTarName));
            throw new RegistryException(re);
        } catch (ExecutionException e) {
            buildContainer(containerBuilder, tarImage, log, true);
            log.info("%s successfully built.", Paths.get(outputDir + "/" + imageTarName));
            throw new ExecutionException(e);
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
            consoleLogger = getConsoleLogger(logger);
            return jibContainerBuilder
                    .containerize(Containerizer.to(image)
                        .setAllowInsecureRegistries(true)
                        .addEventHandler(LogEvent.class, JibBuildServiceUtil::log)
                        .addEventHandler(
                            TimerEvent.class,
                            new TimerEventHandler(message -> consoleLogger.log(LogEvent.Level.DEBUG, message)))
                        .addEventHandler(
                            ProgressEvent.class,
                            new ProgressEventHandler(
                                    update ->
                                            consoleLogger.setFooter(
                                                    ProgressDisplayGenerator.generateProgressDisplay(
                                                            update.getProgress(), update.getUnfinishedLeafTasks())))));

        } catch (CacheDirectoryCreationException | IOException | InterruptedException e) {
            logger.error("Unable to build the image in the offline mode: %s", e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    public static void log(LogEvent event) {
        consoleLogger.log(event.getLevel(), event.getMessage());
    }

    public static ConsoleLogger getConsoleLogger(Logger logger) {
        ConsoleLoggerBuilder consoleLoggerBuilder = ConsoleLoggerBuilder
                .rich(new SingleThreadedExecutor(), true)
                .lifecycle(logger::info);
        if (logger.isDebugEnabled()) {
            consoleLoggerBuilder
                    .debug(logger::debug)
                    .info(logger::info);
        }
        return consoleLoggerBuilder.build();
    }

    public static JibBuildService.JibBuildConfiguration getJibBuildConfiguration(BuildService.BuildServiceConfig config, ImageConfiguration imageConfiguration, Logger log) throws MojoExecutionException {
        io.fabric8.maven.docker.service.BuildService.BuildContext dockerBuildContext = config.getDockerBuildContext();
        RegistryService.RegistryConfig registryConfig = dockerBuildContext.getRegistryConfig();
        BuildImageConfiguration buildImageConfiguration = imageConfiguration.getBuildConfiguration();

        String targetDir = buildImageConfiguration.getAssemblyConfiguration().getTargetDir();

        String outputDir = EnvUtil.prepareAbsoluteOutputDirPath(config.getDockerMojoParameters(), "", "").getAbsolutePath();

        if(targetDir == null) {
            targetDir = "/deployments";
        }

        AuthConfig authConfig = registryConfig.getAuthConfigFactory()
                .createAuthConfig(true, true, registryConfig.getAuthConfig(),
                        registryConfig.getSettings(), null, registryConfig.getRegistry());

        JibBuildService.JibBuildConfiguration.Builder jibBuildConfigurationBuilder = new JibBuildService.JibBuildConfiguration.Builder(log)
                .imageConfiguration(imageConfiguration)
                .mojoParameters(config.getDockerMojoParameters())
                .imageFormat(ImageFormat.Docker)
                .targetDir(targetDir)
                .outputDir(outputDir)
                .buildDirectory(config.getBuildDirectory());
        if(authConfig != null) {
            jibBuildConfigurationBuilder.credential(Credential.from(authConfig.getUsername(), authConfig.getPassword()));
        }

        return jibBuildConfigurationBuilder.build();
    }

    private static JibContainerBuilder getContainerBuilderFromImageConfiguration(ImageConfiguration imageConfiguration, MojoParameters mojoParameters) throws InvalidImageReferenceException {
        if (imageConfiguration.getBuildConfiguration() == null) {
            return null;
        }

        BuildImageConfiguration buildImageConfiguration = imageConfiguration.getBuildConfiguration();
        JibContainerBuilder jibContainerBuilder = Jib.from(getPropertyFromMojoParameter(mojoParameters, FABRIC8_GENERATOR_FROM) != null ?
                getPropertyFromMojoParameter(mojoParameters, FABRIC8_GENERATOR_FROM) : buildImageConfiguration.getFrom());
        if (buildImageConfiguration.getEnv() != null && !buildImageConfiguration.getEnv().isEmpty()) {
            jibContainerBuilder.setEnvironment(buildImageConfiguration.getEnv());
        }

        if (buildImageConfiguration.getPorts() != null && !buildImageConfiguration.getPorts().isEmpty()) {
            jibContainerBuilder.setExposedPorts(getPortSet(buildImageConfiguration.getPorts()));
        }

        if (buildImageConfiguration.getLabels() != null && !buildImageConfiguration.getLabels().isEmpty()) {
            jibContainerBuilder.setLabels(buildImageConfiguration.getLabels());
        }

        if (buildImageConfiguration.getEntryPoint() != null) {
            jibContainerBuilder.setEntrypoint(buildImageConfiguration.getEntryPoint().asStrings());
        }

        if (buildImageConfiguration.getWorkdir() != null) {
            jibContainerBuilder.setWorkingDirectory(AbsoluteUnixPath.get(buildImageConfiguration.getWorkdir()));
        }

        if (buildImageConfiguration.getUser() != null) {
            jibContainerBuilder.setUser(buildImageConfiguration.getUser());
        }

        if (buildImageConfiguration.getVolumes() != null) {
            buildImageConfiguration.getVolumes()
                    .forEach(volumePath -> jibContainerBuilder.addVolume(AbsoluteUnixPath.get(volumePath)));
        }

        jibContainerBuilder.setCreationTime(Instant.now());
        return jibContainerBuilder;
    }

    private static JibContainerBuilder getJibContainerBuilderFromFatJarPath(Path fatJar, String targetDir, JibContainerBuilder containerBuilder) {
        if (fatJar != null) {
            String fatJarName = fatJar.getFileName().toString();
            String jarPath = targetDir + "/" + (fatJarName.isEmpty() ? DEFAULT_JAR_NAME: fatJarName);
            containerBuilder = containerBuilder
                    .addLayer(LayerConfiguration.builder().addEntry(fatJar, AbsoluteUnixPath.get(jarPath)).build());
        }
        return containerBuilder;
    }

    private static RegistryImage getRegistryImage(ImageConfiguration imageConfiguration, MojoParameters mojoParameters, Credential credential) throws InvalidImageReferenceException {
        String username = "", password = "";
        String targetImage = imageConfiguration.getName();
        ImageReference imageReference = ImageReference.parse(targetImage);

        if (imageConfiguration.getBuildConfiguration().getTags() != null) {
            // Pick first not null tag
            String tag = null;
            for (String currentTag : imageConfiguration.getBuildConfiguration().getTags()) {
                if (currentTag != null) {
                    tag = currentTag;
                    break;
                }
            }
            targetImage = new ImageName(imageConfiguration.getName(), tag).getFullName();
        }

        if (credential != null) {
            username = credential.getUsername();
            password = credential.getPassword();

            if (targetImage.contains(DEFAULT_USER_NAME)) {
                targetImage = targetImage.replaceFirst(DEFAULT_USER_NAME, username + "/");
            }
        }

        String registry = getPropertyFromMojoParameter(mojoParameters, FABRIC8_GENERATOR_REGISTRY) != null ?
                getPropertyFromMojoParameter(mojoParameters, FABRIC8_GENERATOR_REGISTRY) : imageConfiguration.getRegistry();
        if (registry != null) {
            imageReference = ImageReference.parse(new ImageName(targetImage).getFullName(registry));
        }

        return RegistryImage.named(imageReference).addCredential(username, password);
    }

    private static Set<Port> getPortSet(List<String> ports) {

        Set<Port> portSet = new HashSet<Port>();
        for(String port : ports) {
            portSet.add(Port.tcp(Integer.parseInt(port)));
        }

        return portSet;
    }

    private static String getPropertyFromMojoParameter(MojoParameters mojoParameters, String propertyName) {
        if (mojoParameters != null && mojoParameters.getProject() != null) {
            Properties properties = mojoParameters.getProject().getProperties();
            if (properties.get(propertyName) != null)
                return properties.get(propertyName).toString();
        }
        return null;
    }

    public static Path getFatJar(String buildDir, Logger log) {
        FatJarDetector fatJarDetector = new FatJarDetector(buildDir);
        try {
            FatJarDetector.Result result = fatJarDetector.scan();
            if(result != null) {
                return result.getArchiveFile().toPath();
            }
        } catch (MojoExecutionException e) {
            throw new UnsupportedOperationException(e);
        }
        return null;
    }
}