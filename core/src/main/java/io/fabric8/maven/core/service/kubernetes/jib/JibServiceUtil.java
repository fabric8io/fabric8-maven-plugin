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

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.ImageFormat;
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
import com.google.cloud.tools.jib.plugins.common.logging.ProgressDisplayGenerator;
import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.config.Arguments;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Class with the static utility methods consumed by JibBuildService.
 */
public class JibServiceUtil {

    private JibServiceUtil() {}

    private static final String DOCKER_REGISTRY = "docker.io";
    private static final String BUSYBOX = "busybox:latest";

    static void addAssemblyFiles(JibContainerBuilder jibContainerBuilder, JibAssemblyManager jibAssemblyManager, AssemblyConfiguration assemblyConfiguration,
                                         MojoParameters mojoParameters, String imageName, Logger log) throws MojoExecutionException, IOException {

        if (hasAssemblyConfiguration(assemblyConfiguration)) {
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

    static void buildContainer(JibContainerBuilder jibContainerBuilder, TarImage image, Logger logger) {
        try {
            jibContainerBuilder.setCreationTime(Instant.now());
            jibContainerBuilder.containerize(Containerizer.to(image)
              .addEventHandler(LogEvent.class, log(logger))
              .addEventHandler(TimerEvent.class, new TimerEventHandler(logger::debug))
              .addEventHandler(ProgressEvent.class, new ProgressEventHandler(logUpdate(logger))));
        } catch (CacheDirectoryCreationException | IOException | ExecutionException | RegistryException ex) {
            logger.error("Unable to build the image tarball: ", ex);
            throw new IllegalStateException(ex);
        } catch (InterruptedException ex) {
            logger.error("Thread interrupted", ex);
            Thread.currentThread().interrupt();
        }
    }

    static JibContainerBuilder containerFromImageConfiguration(ImageConfiguration imageConfiguration)
    throws InvalidImageReferenceException {

        final Optional<BuildImageConfiguration> bic =
          Optional.ofNullable(Objects.requireNonNull(imageConfiguration).getBuildConfiguration());
        final JibContainerBuilder containerBuilder = Jib.from(getBaseImage(imageConfiguration))
          .setFormat(ImageFormat.OCI);
        bic.map(BuildImageConfiguration::getEntryPoint)
          .map(Arguments::asStrings)
          .ifPresent(containerBuilder::setEntrypoint);
        bic.map(BuildImageConfiguration::getEnv)
          .ifPresent(containerBuilder::setEnvironment);
        bic.map(BuildImageConfiguration::getPorts).map(List::stream)
          .map(s -> s.map(Integer::parseInt).map(Port::tcp))
          .map(s -> s.collect(Collectors.toSet()))
          .ifPresent(containerBuilder::setExposedPorts);
        bic.map(BuildImageConfiguration::getLabels)
          .map(Map::entrySet)
          .ifPresent(labels -> labels.forEach(l -> containerBuilder.addLabel(l.getKey(), l.getValue())));
        bic.map(BuildImageConfiguration::getCmd)
          .map(Arguments::asStrings)
          .ifPresent(containerBuilder::setProgramArguments);
        bic.map(BuildImageConfiguration::getUser)
          .ifPresent(containerBuilder::setUser);
        bic.map(BuildImageConfiguration::getVolumes).map(List::stream)
          .map(s -> s.map(AbsoluteUnixPath::get))
          .map(s -> s.collect(Collectors.toSet()))
          .ifPresent(containerBuilder::setVolumes);
        bic.map(BuildImageConfiguration::getWorkdir)
          .filter(((Predicate<String>)String::isEmpty).negate())
          .map(AbsoluteUnixPath::get)
          .ifPresent(containerBuilder::setWorkingDirectory);
        return containerBuilder;
    }

    static String imageNameFromImageConfiguration(ImageConfiguration imageConfiguration) {
        return new ImageName(imageConfiguration.getName()).getRepository();
    }

    /**
     *
     * @param baseImage Base TarImage from where the image will be built.
     * @param targetImageName Full name of the target Image to be pushed to the registry
     * @param credential
     * @param logger
     */
    public static void pushImage(TarImage baseImage, String targetImageName, Credential credential, Logger logger) {
        try {
            RegistryImage targetImage = RegistryImage.named(targetImageName);
            if (credential!= null && !credential.getUsername().isEmpty() && !credential.getPassword().isEmpty()) {
                targetImage.addCredential(credential.getUsername(), credential.getPassword());
            }

            Jib.from(baseImage).containerize(Containerizer.to(targetImage)
                    .addEventHandler(LogEvent.class, log(logger))
                    .addEventHandler(TimerEvent.class, new TimerEventHandler(logger::error))
                    .addEventHandler(ProgressEvent.class, new ProgressEventHandler(logUpdate(logger))));
        } catch (RegistryException | CacheDirectoryCreationException | InvalidImageReferenceException | IOException | ExecutionException | InterruptedException e) {
            logger.error("Exception occured while pushing the image: %s", targetImageName);
            throw new IllegalStateException(e.getMessage(), e);
        }
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

    private static Consumer<LogEvent> log(Logger logger) {
        return logEvent -> ((Function<LogEvent, Consumer<String>>)(le -> {
            switch(le.getLevel()) {
                case ERROR:
                    return logger::error;
                case WARN:
                    return logger::warn;
                case INFO:
                    return logger::info;
                default:
                    return logger::debug;
            }
        })).apply(logEvent).accept(logEvent.getMessage());
    }

    private static Consumer<ProgressEventHandler.Update> logUpdate(Logger logger) {
        return update ->
            ProgressDisplayGenerator.generateProgressDisplay(update.getProgress(), update.getUnfinishedLeafTasks())
              .forEach(logger::info);
    }

    static String getBaseImage(ImageConfiguration imageConfiguration) {
        return Optional.ofNullable(imageConfiguration)
          .map(ImageConfiguration::getBuildConfiguration)
          .map(BuildImageConfiguration::getFrom)
          .filter(((Predicate<String>)String::isEmpty).negate())
          .orElse(BUSYBOX);
    }
}