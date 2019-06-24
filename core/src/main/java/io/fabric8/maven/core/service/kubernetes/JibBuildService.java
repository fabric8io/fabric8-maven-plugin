package io.fabric8.maven.core.service.kubernetes;

import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.RegistryException;
import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.core.service.Fabric8ServiceException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class JibBuildService implements BuildService {

    JibBuildServiceUtil jibBuild;
    public JibBuildService() {
       jibBuild = new JibBuildServiceUtil();
    }

    @Override
    public void build(JibBuildConfigurationUtil buildConfigurationUtil) throws Fabric8ServiceException, InvalidImageReferenceException, InterruptedException, RegistryException, IOException, ExecutionException {
       try {
           jibBuild.buildImage(buildConfigurationUtil);
       }  catch (Exception ex) {
           throw new Fabric8ServiceException("Error while trying to build the image", ex);
       }
    }

    public void build(ImageConfiguration imageConfiguration) throws Fabric8ServiceException {

    }

    @Override
    public void postProcess(BuildServiceConfig config) {

    }
}
