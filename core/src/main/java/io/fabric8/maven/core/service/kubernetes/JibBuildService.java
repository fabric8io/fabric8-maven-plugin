package io.fabric8.maven.core.service.kubernetes;

import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.core.service.Fabric8ServiceException;
import io.fabric8.maven.docker.config.ImageConfiguration;


public class JibBuildService implements BuildService {

    JibBuildServiceUtil jibBuild;
    public JibBuildService() {
       jibBuild = new JibBuildServiceUtil();
    }

    @Override
    public void build(JibBuildConfigurationUtil buildConfigurationUtil) {
       try {
           jibBuild.buildImage(buildConfigurationUtil);
       }  catch (Exception ex) {
           throw new UnsupportedOperationException();
       }
    }

    public void build(ImageConfiguration imageConfiguration) throws UnsupportedOperationException {

    }

    @Override
    public void postProcess(BuildServiceConfig config) {

    }
}
