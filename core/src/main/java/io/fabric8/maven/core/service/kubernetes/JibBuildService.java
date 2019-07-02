package io.fabric8.maven.core.service.kubernetes;

import com.google.cloud.tools.jib.api.Credential;
import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.RegistryService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageName;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JibBuildService implements BuildService {

    // TODO ADD LOGGING
    // TODO CHECK FOR WAR SUPPORT
    private BuildServiceConfig config;

    private JibBuildService() { }

    public JibBuildService (BuildServiceConfig config) {
        Objects.requireNonNull(config, "config");
        this.config = config;
    }

    @Override
    public void build(ImageConfiguration imageConfiguration) {
       try {
           List<String> tags = imageConfiguration.getBuildConfiguration().getTags();

           if (tags.size() > 0) {
               JibBuildConfiguration jibBuildConfiguration;
               for (String tag : tags) {
                   if (tag != null) {
                       String fullName = new ImageName(imageConfiguration.getName(), tag).getFullName();
                       jibBuildConfiguration = JibBuildServiceUtil.getJibBuildConfiguration(config, imageConfiguration, fullName);
                       JibBuildServiceUtil.buildImage(jibBuildConfiguration);
                   }
               }
           }
       } catch (Exception ex) {
           throw new UnsupportedOperationException();
       }
    }



    @Override
    public void postProcess(BuildServiceConfig config) {

    }
}