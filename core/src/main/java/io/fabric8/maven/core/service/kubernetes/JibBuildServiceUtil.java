package io.fabric8.maven.core.service.kubernetes;

import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.Port;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;


import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class JibBuildServiceUtil {

    public JibBuildServiceUtil() {
    }

    public void buildImage(BuildConfiguration buildConfig, JibBuildConfigurationUtil buildConfigUtil) throws InterruptedException, ExecutionException, RegistryException, CacheDirectoryCreationException, IOException {

        ImageReference baseImage = pullBaseImage(buildConfig);
        ImageReference targetImage = pullTargetImage(buildConfig);
        Map<String, String> envMap = buildConfigUtil.getEnvMap();
        Map<String, String> credMap = buildConfigUtil.getCredMap();
        List<String> portList = buildConfigUtil.getPorts();
        Set<Port> portSet = getPortSet(portList);

        buildImage(baseImage, targetImage, envMap, credMap, portSet);
    }

    private ImageReference pullBaseImage(BuildConfiguration buildConfig) {

        ImageReference baseImage = extractBaseFromConfiguration(buildConfig);
        return baseImage;
    }

    private ImageReference pullTargetImage(BuildConfiguration buildConfig) {

        ImageReference targetImage = extractTargetImageFromConfiguration(buildConfig);
        return targetImage;
    }

    private ImageReference extractBaseFromConfiguration(BuildConfiguration buildConfig) {

        ImageConfiguration fromImageConfiguration;
        fromImageConfiguration = buildConfig.getBaseImageConfiguration();
        return fromImageConfiguration.getImage();
    }

    private Set<Port> getPortSet(List<String> ports) {

        Set<Port> portSet = null;
        for(String port : ports) {
            portSet.add(Port.tcp(Integer.parseInt(port)));
        }

        return portSet;
    }

    private ImageReference extractTargetImageFromConfiguration(BuildConfiguration buildConfig) {

        ImageConfiguration toImageConfiguration;
        toImageConfiguration = buildConfig.getTargetImageConfiguration();
        return toImageConfiguration.getImage();
    }

    private void buildImage(ImageReference baseImage, ImageReference targetImage, Map<String, String> envMap, Map<String, String> credMap, Set<Port> portSet) throws InterruptedException, ExecutionException, RegistryException, CacheDirectoryCreationException, IOException {


        String username = credMap.get("username");
        String password = credMap.get("password");

        Jib.from(baseImage).
                setEnvironment(envMap).
                setExposedPorts(portSet).
                containerize(Containerizer.to(RegistryImage.named(targetImage).
                        addCredential(username, password)));

    }

}
