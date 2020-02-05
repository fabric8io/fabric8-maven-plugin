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
import com.google.cloud.tools.jib.api.JibContainerBuilder;
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
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.RegistryService;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Class with the static utility methods consumed by io.fabric8.maven.core.service.kubernetes.JibBuildService.
 */
public class JibServiceUtil {

    private JibServiceUtil() {}

    private static ConsoleLogger consoleLogger;
    private static final String TAR_SUFFIX = ".tar";
    private static final long THREAD_EXECUTOR_TIMEOUT_SECONDS = 60;
    private static final String DOCKER_REGISTRY = "docker.io";
    private static final String BUSYBOX = "busybox:latest";
    /**
     * Builds a container image using JIB
     * @param jibBuildConfiguration
     * @param log
     * @throws InvalidImageReferenceException
     */
    public static void buildImage(JibBuildService.JibBuildConfiguration jibBuildConfiguration, JibAssemblyManager jibAssemblyManager, Logger log)
            throws InvalidImageReferenceException, MojoExecutionException, IOException {

        RegistryImage baseImage = jibBuildConfiguration.getFrom();
        String targetImage = jibBuildConfiguration.getTargetImage();
        Map<String, String> envMap  = jibBuildConfiguration.getEnvMap();
        List<String> portList = jibBuildConfiguration.getPorts();
        Set<Port> portSet = getPortSet(portList);
        List<String> volumes = jibBuildConfiguration.getVolumes();
        String  outputDir = jibBuildConfiguration.getOutputDir();
        String workDir = jibBuildConfiguration.getWorkDir();
        Map<String, String> labels = jibBuildConfiguration.getLabels();
        List<String> entrypointList = new ArrayList<>();
        List<String> cmdList = new ArrayList<>();

        if(jibBuildConfiguration.getEntryPoint() != null) {
            entrypointList = jibBuildConfiguration.getEntryPoint().asStrings();
        }

        if(jibBuildConfiguration.getCmd() != null) {
            cmdList = jibBuildConfiguration.getCmd().asStrings();
        }

        final JibContainerBuilder containerBuilder = Jib.from(baseImage).setFormat(ImageFormat.OCI);

        if (envMap != null) {
            containerBuilder.setEnvironment(envMap);
        }

        if (portSet != null) {
            containerBuilder.setExposedPorts(portSet);
        }

        if (labels != null) {
            labels.entrySet().stream().forEach(entry -> {
                containerBuilder.addLabel(entry.getKey(), entry.getValue());
            });
        }

        if(!entrypointList.isEmpty()) {
            containerBuilder.setEntrypoint(entrypointList);
        }

        if (!cmdList.isEmpty()) {
            containerBuilder.setProgramArguments(cmdList);
        }

        if (workDir!= null && !workDir.isEmpty()) {
            containerBuilder.setWorkingDirectory(AbsoluteUnixPath.get(workDir));
        }

        final Set<AbsoluteUnixPath> volumePaths = new HashSet<>();
        volumes.forEach(volume -> volumePaths.add(AbsoluteUnixPath.get(volume)));
        containerBuilder.setVolumes(volumePaths);

        addAssemblyFiles(containerBuilder, jibAssemblyManager, jibBuildConfiguration.getAssemblyConfiguration(),
                jibBuildConfiguration.getMojoParameters(), targetImage, log);

        if (workDir!= null && !workDir.isEmpty()) {
            containerBuilder.setWorkingDirectory(AbsoluteUnixPath.get(workDir));
        }

        if (jibBuildConfiguration.getUser() != null) {
            containerBuilder.setUser(jibBuildConfiguration.getUser());
        }

        if(!entrypointList.isEmpty()) {
            containerBuilder.setEntrypoint(entrypointList);
        }

        if (!cmdList.isEmpty()) {
            containerBuilder.setProgramArguments(cmdList);
        }

        String imageTarName = ImageReference.parse(targetImage).toString().concat(TAR_SUFFIX);
        TarImage tarImage = TarImage.at(Paths.get(outputDir, imageTarName)).named(targetImage);

        log.info("Building Image Tarball at %s ...", imageTarName);

        buildContainer(containerBuilder, tarImage, log);

        log.info(" %s successfully built.", Paths.get(outputDir, imageTarName));
    }

    private static void addAssemblyFiles(JibContainerBuilder jibContainerBuilder, JibAssemblyManager jibAssemblyManager, AssemblyConfiguration assemblyConfiguration,
                                         MojoParameters mojoParameters, String imageName, Logger log) throws MojoExecutionException, IOException {

        if (hasAssemblyConfiguration(assemblyConfiguration)) {

            if (assemblyConfiguration.getUser() != null && !assemblyConfiguration.getUser().isEmpty()) {
                jibContainerBuilder.setUser(assemblyConfiguration.getUser());
            }

            JibAssemblyManager.BuildDirs buildDirs = createBuildDirs(imageName, mojoParameters);
            JibAssemblyConfigurationSource source =
                    new JibAssemblyConfigurationSource(mojoParameters, buildDirs, assemblyConfiguration);
            jibAssemblyManager.createAssemblyArchive(assemblyConfiguration, source, mojoParameters);

            String ext = assemblyConfiguration.getMode().getExtension().equals("dir") ?
                    "" : ".".concat(assemblyConfiguration.getMode().getExtension());

            File assemblyArchive = new File(source.getOutputDirectory().getPath(), assemblyConfiguration.getName().concat(ext));

            File destination = jibAssemblyManager.extractOrCopy(assemblyConfiguration.getMode(),
                    assemblyArchive, source.getWorkingDirectory(), assemblyConfiguration.getName(), log);

            if (!assemblyConfiguration.getMode().isArchive()) {
                destination = new File(destination, assemblyConfiguration.getName());
            }

            AssemblyConfiguration.PermissionMode mode = assemblyConfiguration.getPermissions();
            if (mode == AssemblyConfiguration.PermissionMode.exec ||
                    mode == AssemblyConfiguration.PermissionMode.auto && EnvUtil.isWindows()) {
                jibAssemblyManager.makeAllFilesExecutable(destination);
            }

            jibAssemblyManager.copyToContainer(jibContainerBuilder, destination, assemblyConfiguration.getTargetDir());
        }
    }


    private static JibAssemblyManager.BuildDirs createBuildDirs(String imageName, MojoParameters params) {
        JibAssemblyManager.BuildDirs buildDirs = new JibAssemblyManager.BuildDirs(imageName, params);
        buildDirs.createDirs();

        return buildDirs;
    }

    private static boolean hasAssemblyConfiguration(AssemblyConfiguration assemblyConfig) {
        return assemblyConfig != null &&
                (assemblyConfig.getInline() != null ||
                        assemblyConfig.getDescriptor() != null ||
                        assemblyConfig.getDescriptorRef() != null);
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


        String baseImage = buildImageConfiguration.getFrom();
        baseImage = baseImage == null || baseImage.isEmpty() ? BUSYBOX : baseImage;

        String pullRegistry = EnvUtil.firstRegistryOf(new ImageName(baseImage).getRegistry(), config.getDockerBuildContext().getRegistryConfig().getRegistry(), imageConfiguration.getRegistry());
        Credential pullCredential = getRegistryCredentials(pullRegistry, config.getDockerBuildContext().getRegistryConfig());

        RegistryImage baseRegistryImage = RegistryImage.named(baseImage);
        if (pullCredential!= null && !pullCredential.getUsername().isEmpty() && !pullCredential.getPassword().isEmpty()) {
            baseRegistryImage.addCredential(pullCredential.getUsername(), pullCredential.getPassword());

        }
        MojoParameters mojoParameters = config.getDockerMojoParameters();
        String outputDir = EnvUtil.prepareAbsoluteOutputDirPath(mojoParameters, "", "").getAbsolutePath();


        JibBuildService.JibBuildConfiguration.Builder jibBuildConfigurationBuilder = new JibBuildService.JibBuildConfiguration
                .Builder()
                .from(baseRegistryImage)
                .envMap(buildImageConfiguration.getEnv())
                .ports(buildImageConfiguration.getPorts())
                .entrypoint(buildImageConfiguration.getEntryPoint())
                .targetImage(new ImageName(imageConfiguration.getName()).getRepository())
                .outputDir(outputDir)
                .labels(buildImageConfiguration.getLabels())
                .volumes(buildImageConfiguration.getVolumes())
                .workingDirectory(buildImageConfiguration.getWorkdir())
                .assemblyConfiguration(buildImageConfiguration.getAssemblyConfiguration())
                .mojoParameters(config.getDockerMojoParameters())
                .user(buildImageConfiguration.getUser())
                .cmd(buildImageConfiguration.getCmd());
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