package io.fabric8.maven.core.handler;

import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.maven.core.config.VolumeConfig;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.utils.Strings;
import mockit.Mocked;
import org.apache.maven.project.MavenProject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ContainerHandlerTest {

    @Mocked
    EnvVarHandler envVarHandler;

    @Mocked
    ProbeHandler probeHandler;

    private List<Container> containers;

    @Test
    public void getContainersTest(){
        MavenProject project = new MavenProject();

        ContainerHandler handler = new ContainerHandler(project, envVarHandler, probeHandler);
        ResourceConfig config = new ResourceConfig.Builder()
                .imagePullPolicy("IfNotPresent")
                .controllerName("testing")
                .withReplicas(5)
                .build();

        List<String> ports = new ArrayList<>();
        ports.add("8080");
        ports.add("9090");

        List<String> tags = new ArrayList<>();
        tags.add("latest");
        tags.add("test");

        BuildImageConfiguration buildImageConfiguration1 = new BuildImageConfiguration.Builder().
                ports(ports).from("fabric8/maven:latest").cleanup("try").tags(tags).compression("gzip").build();

        ImageConfiguration imageConfiguration1 = new ImageConfiguration.Builder().
                name("test").alias("test-app").buildConfig(buildImageConfiguration1).registry("docker.io").build();

        BuildImageConfiguration buildImageConfiguration2 = new BuildImageConfiguration.Builder().
                ports(ports).from("fabric8/").cleanup("try").tags(tags)
                .compression("gzip").dockerFile("testFile").dockerFileDir("/demo").build();

        ImageConfiguration imageConfiguration2 = new ImageConfiguration.Builder().
                name("test").alias("test-app").buildConfig(buildImageConfiguration2).registry("docker.io").build();

        List<ImageConfiguration> images = new ArrayList<>();
        images.add(imageConfiguration1);
        images.add(imageConfiguration2);

        containers = handler.getContainers(config, images);

        assertEquals(2,containers.size());
    }

    @Test
    public void imagePullPolicyTest(){

        //check if policy is set then both in case of version is not null or null

        //project with version and ending in SNAPSHOT
        MavenProject project1 = new MavenProject();
        project1.setVersion("3.5-SNAPSHOT");

        //project with version but not ending in SNAPSHOT
        MavenProject project2 = new MavenProject();
        project2.setVersion("3.5-NEW");

        //project without version
        MavenProject project3 = new MavenProject();

        //creating container Handler for all
        ContainerHandler handler1 = new ContainerHandler(project1, envVarHandler, probeHandler);
        ContainerHandler handler2 = new ContainerHandler(project2, envVarHandler, probeHandler);
        ContainerHandler handler3 = new ContainerHandler(project3, envVarHandler, probeHandler);

        //policy is set in config
        ResourceConfig config = new ResourceConfig.Builder()
                .imagePullPolicy("IfNotPresent").build();

        //a sample image configuration
        BuildImageConfiguration buildImageConfiguration1 = new BuildImageConfiguration.Builder()
                .from("fabric8/maven:latest").build();
        ImageConfiguration imageConfiguration1 = new ImageConfiguration.Builder().
                name("test").alias("test-app").buildConfig(buildImageConfiguration1).registry("docker.io").build();

        List<ImageConfiguration> images = new ArrayList<>();
        images.add(imageConfiguration1);

        containers = handler1.getContainers(config, images);
        assertEquals("IfNotPresent",containers.get(0).getImagePullPolicy());

        containers = handler2.getContainers(config, images);
        assertEquals("IfNotPresent",containers.get(0).getImagePullPolicy());

        //check if policy is not set then both in case of version is set or not
        ResourceConfig config2 = new ResourceConfig.Builder()
                .imagePullPolicy("").build();

        containers = handler1.getContainers(config2, images);
        assertEquals("PullAlways",containers.get(0).getImagePullPolicy());

        containers = handler2.getContainers(config2, images);
        assertEquals("",containers.get(0).getImagePullPolicy());

        containers = handler3.getContainers(config2, images);
        assertEquals("",containers.get(0).getImagePullPolicy());

    }

    @Test
    public void getImageNameTest(){

        MavenProject project = new MavenProject();
        ContainerHandler handler = new ContainerHandler(project, envVarHandler, probeHandler);

        ResourceConfig config = new ResourceConfig.Builder()
                .imagePullPolicy("IfNotPresent").build();

        BuildImageConfiguration buildImageConfiguration1 = new BuildImageConfiguration.Builder()
                .from("fabric8/maven:latest").build();

        //Image Configuration with name and registry
        ImageConfiguration imageConfiguration1 = new ImageConfiguration.Builder().
                name("test").alias("test-app").buildConfig(buildImageConfiguration1).registry("docker.io").build();

        //Image Configuration with name and without registry
        ImageConfiguration imageConfiguration2 = new ImageConfiguration.Builder().
                name("test").alias("test-app").buildConfig(buildImageConfiguration1).build();

        //Image Configuration without name and with registry
        ImageConfiguration imageConfiguration3 = new ImageConfiguration.Builder().
                alias("test-app").buildConfig(buildImageConfiguration1).registry("docker.io").build();

        //Image Configuration without name and registry
        ImageConfiguration imageConfiguration4 = new ImageConfiguration.Builder().
                alias("test-app").buildConfig(buildImageConfiguration1).build();

        List<ImageConfiguration> images = new ArrayList<>();
        images.add(imageConfiguration1);
        images.add(imageConfiguration2);
        images.add(imageConfiguration3);
        images.add(imageConfiguration4);

        containers = handler.getContainers(config, images);

        assertEquals("docker.io/test",containers.get(0).getImage());
        assertEquals("test",containers.get(1).getImage());
        assertNull(containers.get(2).getImage());
        assertNull(containers.get(3).getImage());
    }

    @Test
    public void getvolumeMountTests(){
        MavenProject project = new MavenProject();
        ContainerHandler handler = new ContainerHandler(project, envVarHandler, probeHandler);

        //volumes with volumeconfigs
        List<VolumeConfig> volumes1 = new ArrayList<>();

        //empty volume, no volumeconfigs
        List<VolumeConfig> volumes2 = new ArrayList<>();

        //a sample image configuration
        BuildImageConfiguration buildImageConfiguration1 = new BuildImageConfiguration.Builder()
                .from("fabric8/maven:latest").build();
        ImageConfiguration imageConfiguration1 = new ImageConfiguration.Builder().
                name("test").alias("test-app").buildConfig(buildImageConfiguration1).registry("docker.io").build();

        List<ImageConfiguration> images = new ArrayList<>();
        images.add(imageConfiguration1);

        //volume config without mount
        VolumeConfig volumeConfig1 = new VolumeConfig.Builder().name("first").build();
        volumes1.add(volumeConfig1);
        ResourceConfig config1 = new ResourceConfig.Builder().volumes(volumes1).build();
        containers = handler.getContainers(config1, images);
        assertTrue(containers.get(0).getVolumeMounts().isEmpty());

        List<String> mounts = new ArrayList<>();
        mounts.add("/path/etc");
        //volume config without name but with mount
        VolumeConfig volumeConfig2 = new VolumeConfig.Builder().mounts(mounts).build();
        volumes1.clear();
        volumes1.add(volumeConfig2);
        ResourceConfig config2 = new ResourceConfig.Builder().volumes(volumes1).build();
        containers = handler.getContainers(config2, images);
        assertEquals(1,containers.get(0).getVolumeMounts().size());
        assertEquals(null,containers.get(0).getVolumeMounts().get(0).getName());
        assertEquals("/path/etc",containers.get(0).getVolumeMounts().get(0).getMountPath());

        //volume config with name and single mount
        VolumeConfig volumeConfig3 = new VolumeConfig.Builder().name("third").mounts(mounts).build();
        volumes1.clear();
        volumes1.add(volumeConfig3);
        ResourceConfig config3 = new ResourceConfig.Builder().volumes(volumes1).build();
        containers = handler.getContainers(config3, images);
        assertEquals(1,containers.get(0).getVolumeMounts().size());
        assertEquals("third",containers.get(0).getVolumeMounts().get(0).getName());
        assertEquals("/path/etc",containers.get(0).getVolumeMounts().get(0).getMountPath());


        //volume config with name and multiple mount
        mounts.add("/path/system");
        mounts.add("/path/sys");
        VolumeConfig volumeConfig4 = new VolumeConfig.Builder().name("test").mounts(mounts).build();
        volumes1.clear();
        volumes1.add(volumeConfig4);
        ResourceConfig config4 = new ResourceConfig.Builder().volumes(volumes1).build();
        containers = handler.getContainers(config4, images);
        assertEquals(3,containers.get(0).getVolumeMounts().size());
        for(int i=0;i<=2;i++)
            assertEquals("test",containers.get(0).getVolumeMounts().get(i).getName());

        //empty volume
        ResourceConfig config5 = new ResourceConfig.Builder().volumes(volumes2).build();
        containers = handler.getContainers(config5, images);
        assertTrue(containers.get(0).getVolumeMounts().isEmpty());
    }

    @Test
    public void containerPortsTest(){
        MavenProject project = new MavenProject();
        ContainerHandler handler = new ContainerHandler(project, envVarHandler, probeHandler);
        ResourceConfig config = new ResourceConfig.Builder()
                .imagePullPolicy("IfNotPresent")
                .controllerName("testing")
                .withReplicas(5)
                .build();

        List<String> ports = new ArrayList<>();

        //Empty Ports Array
        BuildImageConfiguration buildImageConfiguration1 = new BuildImageConfiguration.Builder().
                ports(ports).from("fabric8/maven:latest").cleanup("try").compression("gzip").build();

        ImageConfiguration imageConfiguration1 = new ImageConfiguration.Builder().
                name("test").alias("test-app").buildConfig(buildImageConfiguration1).registry("docker.io").build();

        List<ImageConfiguration> images = new ArrayList<>();
        images.add(imageConfiguration1);

        containers = handler.getContainers(config, images);
        assertTrue(containers.get(0).getPorts().isEmpty());

        //without Ports
        buildImageConfiguration1 = new BuildImageConfiguration.Builder().
                from("fabric8/maven:latest").cleanup("try").compression("gzip").build();

        imageConfiguration1 = new ImageConfiguration.Builder().
                name("test").alias("test-app").buildConfig(buildImageConfiguration1).registry("docker.io").build();

        images.clear();
        images.add(imageConfiguration1);

        containers = handler.getContainers(config, images);
        assertTrue(containers.get(0).getPorts().isEmpty());

        //Different kind of Ports Specification
        ports.add("172.22.27.82:82:8082");
        ports.add("172.22.27.81:81:8081/tcp");
        ports.add("172.22.27.83:83:8083/udp");
        ports.add("90:9093/tcp");
        ports.add("172.22.27.84:8084/tcp");
        ports.add("172.22.27.84:84/tcp");
        ports.add("9090/tcp");
        ports.add("9091");
        ports.add("9092/udp");

        buildImageConfiguration1 = new BuildImageConfiguration.Builder().
                ports(ports).from("fabric8/maven:latest").cleanup("try").compression("gzip").build();

        imageConfiguration1 = new ImageConfiguration.Builder().
                name("test").alias("test-app").buildConfig(buildImageConfiguration1).registry("docker.io").build();

        images.clear();
        images.add(imageConfiguration1);

        containers = handler.getContainers(config, images);
        List<ContainerPort> outputports = containers.get(0).getPorts();
        assertEquals(9,outputports.size());
        int protocolCount=0,tcpCount=0,udpCount=0,containerPortCount=0,hostIPCount=0,hostPortCount=0;
        for(int i=0;i<9;i++){
            if(!Strings.isNullOrBlank(outputports.get(i).getProtocol())){
                protocolCount++;
                if(outputports.get(i).getProtocol().equalsIgnoreCase("tcp")){
                    tcpCount++;
                }
                else{
                    udpCount++;
                }
            }
            if(!Strings.isNullOrBlank(outputports.get(i).getHostIP())){
                hostIPCount++;
            }
            if(outputports.get(i).getContainerPort()!=null){
                containerPortCount++;
            }
            if(outputports.get(i).getHostPort()!=null){
                hostPortCount++;
            }
        }
        assertEquals(9,protocolCount);
        assertEquals(7,tcpCount);
        assertEquals(2,udpCount);
        assertEquals(3,hostIPCount);
        assertEquals(9,containerPortCount);
        assertEquals(4,hostPortCount);
    }
}