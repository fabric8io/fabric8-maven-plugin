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

import com.google.cloud.tools.jib.api.*;
import com.google.cloud.tools.jib.event.events.ProgressEvent;
import com.google.cloud.tools.jib.event.events.TimerEvent;
import com.google.cloud.tools.jib.event.progress.ProgressEventHandler;
import com.google.cloud.tools.jib.plugins.common.TimerEventHandler;
import com.google.cloud.tools.jib.plugins.common.logging.ProgressDisplayGenerator;
import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.RegistryService;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
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
    private static String EMPTY_STRING = "";
    private static String TAR_POSTFIX = ".tar";

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
     * @param imageConfiguration ImageConfiguration
     * @param project MavenProject
     * @param registryConfig  RegistryService.RegistryConfig
     * @param outputDirectory Target Output Directory
     * @param log Logger
     * @throws MojoExecutionException
     */
    public static void jibPush(ImageConfiguration imageConfiguration, MavenProject project, RegistryService.RegistryConfig registryConfig,
                                 String outputDirectory, Logger log) throws MojoExecutionException {
        BuildImageConfiguration buildImageConfiguration = imageConfiguration.getBuildConfiguration();

        String outputDir = prepareAbsoluteOutputDirPath(EMPTY_STRING, project, outputDirectory).getAbsolutePath();

        ImageName tarImage = new ImageName(imageConfiguration.getName());
        String tarImageRepo = tarImage.getRepository();
        try {
            String imageTarName = ImageReference.parse(tarImageRepo).toString().concat(TAR_POSTFIX);
            TarImage baseImage = TarImage.at(Paths.get(outputDir, imageTarName));

            String configuredRegistry = EnvUtil.firstRegistryOf((new ImageName(imageConfiguration.getName())).getRegistry(), imageConfiguration.getRegistry(), registryConfig.getRegistry());

            Credential pushCredential = getRegistryCredentials(configuredRegistry, registryConfig);
            final List<String> tags = buildImageConfiguration.getTags();
            if (tags.isEmpty()) {
                final String targetImage = new ImageName(imageConfiguration.getName()).getFullName();
                pushImage(baseImage, targetImage, pushCredential, log);
            } else {
                tags.stream().filter(Objects::nonNull).forEach(tag -> {
                    final String targetImage = new ImageName(imageConfiguration.getName(), tag).getFullName();
                    pushImage(baseImage, targetImage, pushCredential, log);
                });
            }
        } catch (InvalidImageReferenceException | IllegalStateException e) {
            log.error("Exception occurred while pushing the image: %s", imageConfiguration.getName());
            throw new MojoExecutionException(e.getMessage(), e);

        }
    }

    /**
     *
     * @param baseImage Base TarImage from where the image will be built.
     * @param targetImageName Full name of the target Image to be pushed to the registry
     * @param credential
     * @param logger
     */
    private static void pushImage(TarImage baseImage, String targetImageName, Credential credential, Logger logger) {
        try {
            RegistryImage targetImage = RegistryImage.named(targetImageName);
            if (credential!= null && !credential.getUsername().isEmpty() && !credential.getPassword().isEmpty()) {
                targetImage.addCredential(credential.getUsername(), credential.getPassword());
            }

            Jib.from(baseImage).containerize(Containerizer.to(targetImage)
                    .addEventHandler(LogEvent.class, log(logger))
                    .addEventHandler(TimerEvent.class, new TimerEventHandler(logger::debug))
                    .addEventHandler(ProgressEvent.class, new ProgressEventHandler(logUpdate(logger))));
        } catch (RegistryException | CacheDirectoryCreationException | InvalidImageReferenceException | IOException | ExecutionException | InterruptedException e) {
            logger.error("Exception occured while pushing the image: %s", targetImageName);
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static Credential getRegistryCredentials(String registry, RegistryService.RegistryConfig registryConfig) throws MojoExecutionException {
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

    private static File prepareAbsoluteOutputDirPath(String path, MavenProject project, String outputDirectory) {
        File file = new File(path);
        return file.isAbsolute() ? file : new File(new File(project.getBasedir(), (new File(outputDirectory)).toString()), path);
    }

}