package io.fabric8.maven.core.service.kubernetes;

import com.google.cloud.tools.jib.api.*;
import com.google.cloud.tools.jib.registry.credentials.DockerConfigCredentialRetriever;
import io.fabric8.maven.core.model.Dependency;
import io.fabric8.maven.core.model.GroupArtifactVersion;
import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.core.service.Fabric8ServiceException;
import io.fabric8.maven.core.util.FatJarDetector;
import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.RegistryAuthConfiguration;
import io.fabric8.maven.docker.service.RegistryService;
import io.fabric8.maven.docker.util.ImageName;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;


import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class JibBuildServiceUtil {

    private JibBuildServiceUtil() {}

    public static void buildImage(JibBuildConfiguration buildConfiguration) throws InvalidImageReferenceException {

        String fromImage = buildConfiguration.getFrom();
        String targetImage = buildConfiguration.getTargetImage();
        Credential credential = buildConfiguration.getCredential();
        Map<String, String> envMap  = buildConfiguration.getEnvMap();
        List<String> portList = buildConfiguration.getPorts();
        Set<Port> portSet = getPortSet(portList);

        buildImage(fromImage, targetImage, envMap, credential, portSet, buildConfiguration.getFatJar());
    }

    private static Set<Port> getPortSet(List<String> ports) {

        Set<Port> portSet = new HashSet<Port>();
        for(String port : ports) {
            portSet.add(Port.tcp(Integer.parseInt(port)));
        }

        return portSet;
    }

    protected static JibContainer buildImage(String baseImage, String targetImage, Map<String, String> envMap, Credential credential, Set<Port> portSet, Path fatJar) throws InvalidImageReferenceException {

        JibContainerBuilder contBuild = Jib.from(baseImage);

        if (!envMap.isEmpty()) {
            contBuild = contBuild.setEnvironment(envMap);
        }

        if (!portSet.isEmpty()) {
            contBuild = contBuild.setExposedPorts(portSet);
        }

        if (fatJar != null) {
            // TODO parameterize pathInContainer to targetDir
            contBuild = contBuild
                    .addLayer(LayerConfiguration.builder().addEntry(fatJar, AbsoluteUnixPath.get("/app")).build());

        }

        // TODO ADD ENTRYPOINT!!!

        if (credential != null) {
            String username = credential.getUsername();
            String password = credential.getPassword();

        try {
                return contBuild.containerize(
                        Containerizer.to(RegistryImage.named(targetImage)
                                .addCredential(username, password)));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return null;
    }

    public static JibBuildConfiguration getJibBuildConfiguration(BuildService.BuildServiceConfig config, MavenProject project, ImageConfiguration imageConfiguration, String fullImageName) throws MojoExecutionException {
        BuildImageConfiguration buildImageConfiguration = imageConfiguration.getBuildConfiguration();

        RegistryService.RegistryConfig registryConfig = config.getDockerBuildContext().getRegistryConfig();

        AuthConfig authConfig = registryConfig.getAuthConfigFactory()
                .createAuthConfig(true, true, registryConfig.getAuthConfig(),
                        registryConfig.getSettings(), null, registryConfig.getRegistry());

        return  new JibBuildConfiguration.Builder().from(buildImageConfiguration.getFrom())
                .envMap(buildImageConfiguration.getEnv())
                .ports(buildImageConfiguration.getPorts())
                .credential(Credential.from(authConfig.getUsername(), authConfig.getPassword()))
                .targetImage(fullImageName)
                .pushRegistry(registryConfig.getRegistry())
                .project(project)
                .build();
    }

    public static Path getFatJar(MavenProject project) {
        FatJarDetector fatJarDetector = new FatJarDetector(project.getBuild().getOutputDirectory());
        try {
            FatJarDetector.Result result = fatJarDetector.scan();
            return result.getArchiveFile().toPath();
        } catch (MojoExecutionException e) {
            // TODO log.err("MOJO EXEC EXCEPTION!")
            throw new UnsupportedOperationException();
        }
    }

    private static String extractBaseImage(BuildImageConfiguration buildImgConfig) {

        String fromImage = buildImgConfig.getFrom();
        if (fromImage == null) {
            AssemblyConfiguration assemblyConfig = buildImgConfig.getAssemblyConfiguration();
            if (assemblyConfig == null) {
                fromImage = "busybox";
            }
        }
        return fromImage;
    }

    public static List<Path> getDependencies(boolean transitive, MavenProject project) {
        final Set<Artifact> artifacts = transitive ?
                project.getArtifacts() : project.getDependencyArtifacts();

        final List<Dependency> dependencies = new ArrayList<>();

        for (Artifact artifact : artifacts) {
            dependencies.add(
                    new Dependency(new GroupArtifactVersion(artifact.getGroupId(),
                            artifact.getArtifactId(),
                            artifact.getVersion()),
                            artifact.getType(),
                            artifact.getScope(),
                            artifact.getFile()));
        }

        final List<Path> dependenciesPath = new ArrayList<>();
        for(Dependency dep : dependencies) {
            File depLocation = dep.getLocation();
            Path depPath = depLocation.toPath();
            dependenciesPath.add(depPath);
        }
        return dependenciesPath;
    }
}