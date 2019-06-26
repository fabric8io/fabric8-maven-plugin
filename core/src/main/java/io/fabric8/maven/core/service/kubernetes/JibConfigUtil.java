package io.fabric8.maven.core.service.kubernetes;

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.CredentialRetriever;
import com.google.cloud.tools.jib.registry.credentials.DockerConfigCredentialRetriever;
import io.fabric8.maven.core.model.Dependency;
import io.fabric8.maven.core.model.GroupArtifactVersion;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.RegistryAuthConfiguration;
import io.fabric8.maven.docker.util.ImageName;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
                target(extractTargetImage(to)).
                ports(buildImgConfig.getPorts()).
                credMap(extractCredential(authConfig, to)).
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

    public String extractTargetImage(String to) {
        String registry = "docker.io";
        if(to != null) {
            return to;
        } else {
            String imageName = imageconfig.getName();
            ImageName processedName = new ImageName(imageName);
            if(processedName.hasRegistry()) {
                registry = processedName.getRegistry();
            }

            return registry + "/" + processedName.getRepository() + ":" + processedName.getTag();
        }
    }

    public Map<String, String> extractCredential(RegistryAuthConfiguration authConfig, String to)  {
        if(authConfig != null) {
            return authConfig.toMap();
        } else {
            ImageName targetImage = new ImageName(imageconfig.getName());
            String registry = targetImage.hasRegistry() ? targetImage.getRegistry() : "docker.io";
            Map<String, String> credMap = getDockerConfigCredential(registry);
            return credMap;
        }
    }

    public Map<String, String> getDockerConfigCredential(String registry)  {
        DockerConfigCredentialRetriever credRetiever = new DockerConfigCredentialRetriever(registry);
        Map<String, String> m = new HashMap<>();
        try{
            Optional<Credential> optCred= credRetiever.retrieve(null);
            Credential credential = optCred.get();
            if(credential != null) {
                m.put("username", credential.getUsername());
                m.put("password", credential.getPassword());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return m;
    }
}
