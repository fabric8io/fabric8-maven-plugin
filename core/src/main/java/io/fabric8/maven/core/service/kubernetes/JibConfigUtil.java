package io.fabric8.maven.core.service.kubernetes;

import io.fabric8.maven.core.model.Dependency;
import io.fabric8.maven.core.model.GroupArtifactVersion;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.RegistryAuthConfiguration;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class JibConfigUtil {

    ImageConfiguration imageconfig;
    public JibConfigUtil(ImageConfiguration imgConf) {
        this.imageconfig = imgConf;
    }

    public JibBuildConfigurationUtil getUtil(BuildImageConfiguration buildImgConfig, RegistryAuthConfiguration authConfig, String to, MavenProject project) {
        return new JibBuildConfigurationUtil.Builder().
                from(extractBaseImage(buildImgConfig)).
                envMap(buildImgConfig.getEnv()).
                target(to).
                ports(buildImgConfig.getPorts()).
                credMap(authConfig.toMap()).
                depList(getDependencies(true, project)).
                build();
    }

    public String extractBaseImage(BuildImageConfiguration buildImgConfig) {

        String fromImage;
        fromImage = buildImgConfig.getFrom();
        if (fromImage == null) {
            AssemblyConfiguration assemblyConfig = buildImgConfig.getAssemblyConfiguration();
            if (assemblyConfig == null) {
                fromImage = "busybox";
            }
        }
        return fromImage;
    }

    public List<Path> getDependencies(boolean transitive, MavenProject project) {
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
