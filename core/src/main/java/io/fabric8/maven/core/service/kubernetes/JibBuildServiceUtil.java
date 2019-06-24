package io.fabric8.maven.core.service.kubernetes;

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.Port;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import io.fabric8.maven.core.service.Fabric8ServiceException;


import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class JibBuildServiceUtil {

    public JibBuildServiceUtil() {

    }

    public void buildImage(JibBuildConfigurationUtil buildConfigurationUtil) throws Fabric8ServiceException, InvalidImageReferenceException , InterruptedException, RegistryException, IOException,  ExecutionException {

        String fromImage = buildConfigurationUtil.getFrom();
        String targetImage = buildConfigurationUtil.getTo();
        Map<String, String> credMap = buildConfigurationUtil.getCredMap();;
        Map<String, String> envMap  = buildConfigurationUtil.getEnvMap();;
        List<Path> dependencyList = buildConfigurationUtil.getDependencyList();
        List<String> portList = buildConfigurationUtil.getPorts();
        Set<Port> portSet = getPortSet(portList);;

        buildImage(fromImage, targetImage, envMap, credMap, portSet, dependencyList);
    }

    private Set<Port> getPortSet(List<String> ports) {

        Set<Port> portSet = new HashSet<Port>();;
        for(String port : ports) {
            portSet.add(Port.tcp(Integer.parseInt(port)));
        }

        return portSet;
    }

    protected void buildImage(String baseImage, String targetImage, Map<String, String> envMap, Map<String, String> credMap, Set<Port> portSet, List<Path> dependencyList) throws InvalidImageReferenceException , InterruptedException, RegistryException, IOException,  ExecutionException {

        JibContainerBuilder contBuild = Jib.from(baseImage);

        if(dependencyList.size() > 0) {
            contBuild = contBuild.addLayer(dependencyList, AbsoluteUnixPath.get("/path/in/container"));
        }

        if(envMap.size() > 0) {
            contBuild = contBuild.setEnvironment(envMap);
        }

        if(portSet.size() > 0) {
            contBuild = contBuild.setExposedPorts(portSet);
        }

        if(credMap.size() > 0) {
            String username = credMap.get("username");
            String password = credMap.get("password");
        }

        try {
            contBuild.containerize(
                    Containerizer.to(RegistryImage.named(targetImage)
                            .addCredential("myusername", "mypassword")));
        } catch (CacheDirectoryCreationException e) {
            e.printStackTrace();
        }

    }

}
