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
import com.google.cloud.tools.jib.api.CredentialRetriever;
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
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.cloud.tools.jib.plugins.common.TimerEventHandler;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLoggerBuilder;
import com.google.cloud.tools.jib.plugins.common.logging.ProgressDisplayGenerator;
import com.google.cloud.tools.jib.plugins.common.logging.SingleThreadedExecutor;
import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.core.service.kubernetes.JibBuildService;
import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
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
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Class with the static utility methods consumed by io.fabric8.maven.core.service.kubernetes.JibBuildService.
 */
public class JibServiceUtil {

    private JibServiceUtil() {}

    private static final String DEFAULT_JAR_NAME = "/app.jar";
    private static ConsoleLogger consoleLogger;
    private static final String TAR_SUFFIX = ".tar";
    private static final long THREAD_EXECUTOR_TIMEOUT_SECONDS = 60;
    private static final String DOCKER_REGISTRY = "docker.io";

    /**
     * Builds a container image using JIB
     * @param jibBuildConfiguration
     * @param log
     * @throws InvalidImageReferenceException
     */
    public static void buildImage(JibBuildService.JibBuildConfiguration jibBuildConfiguration, Logger log) throws InvalidImageReferenceException {

        RegistryImage fromImage = jibBuildConfiguration.getFrom();
        String targetImage = jibBuildConfiguration.getTargetImage();
        Map<String, String> envMap  = jibBuildConfiguration.getEnvMap();
        List<String> portList = jibBuildConfiguration.getPorts();
        Set<Port> portSet = getPortSet(portList);
        List<String> volumes = jibBuildConfiguration.getVolumes();
        String  outputDir = jibBuildConfiguration.getOutputDir();
        String targetDir = jibBuildConfiguration.getTargetDir();
        String workDir = jibBuildConfiguration.getWorkDir();
        Path fatJar = jibBuildConfiguration.getFatJar();
        Map<String, String> labels = jibBuildConfiguration.getLabels();
        List<String> entrypointList = new ArrayList<>();
        if(jibBuildConfiguration.getEntryPoint() != null) {
            entrypointList = jibBuildConfiguration.getEntryPoint().asStrings();
        }

        buildImage(fromImage, targetImage, envMap, labels, portSet, fatJar, entrypointList, targetDir, outputDir, workDir, volumes, log);
    }

    /**
     * Builds a container image using Jib from all the following parameters:
     *
     * @param baseImage
     * @param targetImage
     * @param envMap
     * @param portSet
     * @param fatJar
     * @param entrypointList
     * @param targetDir
     * @param outputDir
     * @param log
     * @return
     * @throws InvalidImageReferenceException
     */
    protected static void buildImage(RegistryImage baseImage, String targetImage, Map<String, String> envMap, Map<String, String> labels, Set<Port> portSet, Path fatJar, List<String> entrypointList, String targetDir, String outputDir, String workDir, List<String> volumes, Logger log) throws InvalidImageReferenceException {

        final JibContainerBuilder contBuild = Jib.from(baseImage);

        if (envMap != null) {
            contBuild.setEnvironment(envMap);
        }

        if (portSet != null) {
            contBuild.setExposedPorts(portSet);
        }

        if (labels != null) {
            labels.entrySet().stream().forEach(entry -> {
                contBuild.addLabel(entry.getKey(), entry.getValue());
            });
        }

        if (fatJar != null) {
            String fatJarName = fatJar.getFileName().toString();
            String jarPath = targetDir + "/" + (fatJarName.isEmpty() ? DEFAULT_JAR_NAME: fatJarName);
            contBuild.addLayer(LayerConfiguration.builder().addEntry(fatJar, AbsoluteUnixPath.get(jarPath)).build());
        }

        if(!entrypointList.isEmpty()) {
            contBuild.setEntrypoint(entrypointList);
        }

        if (workDir!= null && !workDir.isEmpty()) {
            contBuild.setWorkingDirectory(AbsoluteUnixPath.get(workDir));
        }

        contBuild.setFormat(ImageFormat.OCI);

        final Set<AbsoluteUnixPath> volumePaths = new HashSet<>();
        volumes.forEach(volume -> volumePaths.add(AbsoluteUnixPath.get(volume)));
        contBuild.setVolumes(volumePaths);

        String imageTarName = ImageReference.parse(targetImage).toString().concat(TAR_SUFFIX);
        TarImage tarImage = TarImage.at(Paths.get(outputDir, imageTarName)).named(targetImage);

        log.info("Building Image Tarball at %s ...", imageTarName);

        buildContainer(contBuild, tarImage, log);

        log.info(" %s successfully built.", Paths.get(outputDir, imageTarName));
    }

    public static void buildContainer(JibContainerBuilder jibContainerBuilder, TarImage image, Logger logger) {
        SingleThreadedExecutor singleThreadedExecutor = new SingleThreadedExecutor();
        try {

            consoleLogger = getConsoleLogger(logger, singleThreadedExecutor);
            jibContainerBuilder.setCreationTime(Instant.now());

             jibContainerBuilder.containerize(Containerizer.to(image)
                    .addEventHandler(LogEvent.class, JibServiceUtil::log)
                    .addEventHandler(TimerEvent.class,
                            new TimerEventHandler(message -> consoleLogger.log(LogEvent.Level.DEBUG, message)))
                    .addEventHandler(ProgressEvent.class,
                            new ProgressEventHandler(
                                    update -> consoleLogger.setFooter(
                                                    ProgressDisplayGenerator.generateProgressDisplay(
                                                            update.getProgress(), update.getUnfinishedLeafTasks())))));

            singleThreadedExecutor.shutDownAndAwaitTermination(Duration.ofSeconds(THREAD_EXECUTOR_TIMEOUT_SECONDS));
        } catch (CacheDirectoryCreationException | IOException | InterruptedException | ExecutionException | RegistryException ex) {

            singleThreadedExecutor.shutDownAndAwaitTermination(Duration.ofSeconds(THREAD_EXECUTOR_TIMEOUT_SECONDS));

            logger.error("Unable to build the image tarball: ", ex);
            throw new IllegalStateException(ex);
        }
    }

    public static void log(LogEvent event) {
        consoleLogger.log(event.getLevel(), event.getMessage());
    }

    public static ConsoleLogger getConsoleLogger(Logger logger, SingleThreadedExecutor executor) {
        ConsoleLoggerBuilder consoleLoggerBuilder = ConsoleLoggerBuilder
                .rich(executor, true)
                .progress(logger::info)
                .lifecycle(logger::info);
        if (logger.isDebugEnabled()) {
            consoleLoggerBuilder
                    .debug(logger::debug)
                    .info(logger::info);
        }
        return consoleLoggerBuilder.build();
    }

    public static JibBuildService.JibBuildConfiguration getJibBuildConfiguration(BuildService.BuildServiceConfig config, ImageConfiguration imageConfiguration, Logger log) throws MojoExecutionException, InvalidImageReferenceException {
        BuildImageConfiguration buildImageConfiguration = imageConfiguration.getBuildConfiguration();


        String pullRegistry = EnvUtil.firstRegistryOf(new ImageName(buildImageConfiguration.getFrom()).getRegistry(), config.getDockerBuildContext().getRegistryConfig().getRegistry(), imageConfiguration.getRegistry());
        Credential pullCredential = getRegistryCredentials(pullRegistry, config.getDockerBuildContext().getRegistryConfig());

        String targetDir = buildImageConfiguration.getAssemblyConfiguration().getTargetDir();

        MojoParameters mojoParameters = config.getDockerMojoParameters();
        String outputDir = EnvUtil.prepareAbsoluteOutputDirPath(mojoParameters, "", "").getAbsolutePath();

        if(targetDir == null) {
            targetDir = "/deployments";
        }

        JibBuildService.JibBuildConfiguration.Builder jibBuildConfigurationBuilder = new JibBuildService.JibBuildConfiguration
                .Builder(log)
                .from(RegistryImage.named(buildImageConfiguration.getFrom()).addCredential(pullCredential.getUsername(), pullCredential.getPassword()))
                .envMap(buildImageConfiguration.getEnv())
                .ports(buildImageConfiguration.getPorts())
                .entrypoint(buildImageConfiguration.getEntryPoint())
                .targetImage(new ImageName(imageConfiguration.getName()).getRepository())
                .targetDir(targetDir)
                .outputDir(outputDir)
                .labels(buildImageConfiguration.getLabels())
                .volumes(buildImageConfiguration.getVolumes())
                .workingDirectory(buildImageConfiguration.getWorkdir())
                .buildDirectory(config.getBuildDirectory());
        return jibBuildConfigurationBuilder.build();
    }

    /**
     *
     * @param baseImage Base TarImage from where the image will be built.
     * @param targetImageName Full name of the target Image to be pushed to the registry
     * @param credential
     * @param logger
     * @throws InvalidImageReferenceException
     */
    public static void pushImage(TarImage baseImage, String targetImageName, Credential credential, SingleThreadedExecutor executor, Logger logger, long timeout) throws IllegalStateException {
        try {
            RegistryImage targetImage = RegistryImage.named(targetImageName);
            consoleLogger = getConsoleLogger(logger, executor);
            if (credential!= null && !credential.getUsername().isEmpty() && !credential.getPassword().isEmpty()) {
                targetImage.addCredential(credential.getUsername(), credential.getPassword());
            }

            Jib.from(baseImage).containerize(Containerizer.to(targetImage)
                    .addEventHandler(LogEvent.class, JibServiceUtil::log)
                    .addEventHandler(TimerEvent.class,
                            new TimerEventHandler(message -> consoleLogger.log(LogEvent.Level.ERROR, message)))
                    .addEventHandler(ProgressEvent.class,
                            new ProgressEventHandler(
                                    update ->  consoleLogger.setFooter(
                                                ProgressDisplayGenerator.generateProgressDisplay(
                                                    update.getProgress(), update.getUnfinishedLeafTasks())))));

        } catch (RegistryException | CacheDirectoryCreationException | InvalidImageReferenceException | IOException | ExecutionException | InterruptedException e) {
            logger.error("Exception occured while pushing the image: %s", targetImageName);
            throw new IllegalStateException(e.getMessage(), e);
        }
    }



    private static Set<Port> getPortSet(List<String> ports) {

        Set<Port> portSet = new HashSet<>();
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

    public static Credential getRegistryCredentials(String registry, RegistryService.RegistryConfig registryConfig) throws MojoExecutionException {
        if (registry == null) {
            registry = DOCKER_REGISTRY; // Let's assume docker is default registry.
        }

        AuthConfig authConfig = registryConfig.getAuthConfigFactory()
                .createAuthConfig(true, true, registryConfig.getAuthConfig(),
                        registryConfig.getSettings(), null, registry);

        if (authConfig != null) {
            return Credential.from(authConfig.getUsername(), authConfig.getPassword());
        }
        return null;
    }
}