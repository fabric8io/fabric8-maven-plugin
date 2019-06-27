package io.fabric8.maven.core.service.kubernetes;

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.Port;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.RegistryImage;
import io.fabric8.maven.core.service.Fabric8ServiceException;


import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class JibBuildServiceUtil {

    public JibBuildServiceUtil() {

    }

    public void buildImage(JibBuildConfigurationUtil buildConfigurationUtil) throws Fabric8ServiceException, InvalidImageReferenceException, IOException, CacheDirectoryCreationException {

        String fromImage = buildConfigurationUtil.getFrom();
        String targetImage = buildConfigurationUtil.getTo();
        Map<String, String> credMap = buildConfigurationUtil.getCredMap();
        Map<String, String> envMap  = buildConfigurationUtil.getEnvMap();
        List<Path> dependencyList = buildConfigurationUtil.getDependencyList();
        List<String> portList = buildConfigurationUtil.getPorts();
        Set<Port> portSet = getPortSet(portList);

        buildImage(fromImage, targetImage, envMap, credMap, portSet, dependencyList);
    }

    private Set<Port> getPortSet(List<String> ports) {

        Set<Port> portSet = new HashSet<Port>();
        for(String port : ports) {
            portSet.add(Port.tcp(Integer.parseInt(port)));
        }

        return portSet;
    }

    protected void buildImage(String baseImage, String targetImage, Map<String, String> envMap, Map<String, String> credMap, Set<Port> portSet, List<Path> dependencyList) throws InvalidImageReferenceException , IOException {

        JibContainerBuilder contBuild = Jib.from(baseImage);

        if(!envMap.isEmpty()) {
            contBuild = contBuild.setEnvironment(envMap);
        }

        if(!portSet.isEmpty()) {
            contBuild = contBuild.setExposedPorts(portSet);
        }

        if(!dependencyList.isEmpty()) {
            contBuild = contBuild.addLayer(dependencyList, AbsoluteUnixPath.get("/path/in/container"));
        }

        if(!credMap.isEmpty()) {
            String username = credMap.get("username");
            String password = credMap.get("password");

            try {
                contBuild.containerize(
                        Containerizer.to(RegistryImage.named(targetImage)
                                .addCredential(username, password)));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

}
