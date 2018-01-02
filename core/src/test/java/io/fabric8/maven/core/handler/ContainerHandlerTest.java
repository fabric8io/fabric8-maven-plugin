/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

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

    MavenProject project = new MavenProject();

    MavenProject project1 = new MavenProject();

    MavenProject project2 = new MavenProject();

    ResourceConfig config = new ResourceConfig.Builder()
            .imagePullPolicy("IfNotPresent")
            .controllerName("testing")
            .withReplicas(5)
            .build();

    //policy is set in config
    ResourceConfig config1 = new ResourceConfig.Builder()
            .imagePullPolicy("IfNotPresent").build();

    List<String> ports = new ArrayList<>();

    List<String> tags = new ArrayList<>();

    List<ImageConfiguration> images = new ArrayList<>();

    //volumes with volumeconfigs
    List<VolumeConfig> volumes1 = new ArrayList<>();

    //empty volume, no volumeconfigs
    List<VolumeConfig> volumes2 = new ArrayList<>();

    //a sample image configuration
    BuildImageConfiguration buildImageConfiguration1 = new BuildImageConfiguration.Builder()
            .from("fabric8/maven:latest").build();
    ImageConfiguration imageConfiguration1 = new ImageConfiguration.Builder().
            name("test").alias("test-app").buildConfig(buildImageConfiguration1).registry("docker.io").build();

    @Test
    public void getContainersWithAliasTest() {

        project.setArtifactId("test-artifact");
        project.setGroupId("test-group");

        ports.add("8080");
        ports.add("9090");

        tags.add("latest");
        tags.add("test");

        ContainerHandler handler = new ContainerHandler(project, envVarHandler, probeHandler);

        //container name with alias
        BuildImageConfiguration buildImageConfiguration = new BuildImageConfiguration.Builder().
                ports(ports).from("fabric8/maven:latest").cleanup("try").tags(tags).compression("gzip").build();

        ImageConfiguration imageConfiguration = new ImageConfiguration.Builder().
                name("test").alias("test-app").buildConfig(buildImageConfiguration).registry("docker.io").build();

        images.clear();
        images.add(imageConfiguration);

        containers = handler.getContainers(config, images);
        assertNotNull(containers);
        assertEquals("test-app", containers.get(0).getName());
        assertEquals("docker.io/test", containers.get(0).getImage());
        assertEquals("IfNotPresent", containers.get(0).getImagePullPolicy());
    }

    @Test
    public void getContainerWithGroupArtifactTest() {

        project.setArtifactId("test-artifact");
        project.setGroupId("test-group");

        ports.add("8080");
        ports.add("9090");

        tags.add("latest");
        tags.add("test");

        ContainerHandler handler = new ContainerHandler(project, envVarHandler, probeHandler);
        //container name with group id and aritact id without alias and user
        BuildImageConfiguration buildImageConfiguration = new BuildImageConfiguration.Builder().
                ports(ports).from("fabric8/").cleanup("try").tags(tags)
                .compression("gzip").dockerFile("testFile").dockerFileDir("/demo").build();

        ImageConfiguration imageConfiguration = new ImageConfiguration.Builder().
                name("test").buildConfig(buildImageConfiguration).registry("docker.io").build();

        images.clear();
        images.add(imageConfiguration);

        containers = handler.getContainers(config, images);
        assertNotNull(containers);
        assertEquals("test-group-test-artifact", containers.get(0).getName());
        assertEquals("docker.io/test", containers.get(0).getImage());
        assertEquals("IfNotPresent", containers.get(0).getImagePullPolicy());
    }
    @Test
    public void getContainerTestWithUser(){
        project.setArtifactId("test-artifact");
        project.setGroupId("test-group");

        ports.add("8080");
        ports.add("9090");

        tags.add("latest");
        tags.add("test");

        //container name with user and image with tag
        ContainerHandler handler = new ContainerHandler(project, envVarHandler, probeHandler);

        BuildImageConfiguration buildImageConfiguration = new BuildImageConfiguration.Builder().
                ports(ports).from("fabric8/").cleanup("try").tags(tags)
                .compression("gzip").dockerFile("testFile").dockerFileDir("/demo").build();

        ImageConfiguration imageConfiguration = new ImageConfiguration.Builder().
                name("user/test:latest").buildConfig(buildImageConfiguration).registry("docker.io").build();

        images.clear();
        images.add(imageConfiguration);

        containers = handler.getContainers(config, images);
        assertNotNull(containers);
        assertEquals("user-test-artifact",containers.get(0).getName());
        assertEquals("docker.io/user/test:latest",containers.get(0).getImage());
        assertEquals("IfNotPresent",containers.get(0).getImagePullPolicy());
    }

    @Test
    public void imagePullPolicyWithPolicySetTest() {

        //check if policy is set then both in case of version is not null or null

        //project with version and ending in SNAPSHOT
        project1.setVersion("3.5-SNAPSHOT");

        //project with version but not ending in SNAPSHOT
        project2.setVersion("3.5-NEW");

        //creating container Handler for all
        ContainerHandler handler1 = new ContainerHandler(project1, envVarHandler, probeHandler);
        ContainerHandler handler2 = new ContainerHandler(project2, envVarHandler, probeHandler);

        images.clear();
        images.add(imageConfiguration1);

        containers = handler1.getContainers(config1, images);
        assertEquals("IfNotPresent", containers.get(0).getImagePullPolicy());

        containers = handler2.getContainers(config1, images);
        assertEquals("IfNotPresent", containers.get(0).getImagePullPolicy());
    }
    @Test
    public void imagePullPolicyWithoutPolicySetTest(){

        //project with version and ending in SNAPSHOT
        project1.setVersion("3.5-SNAPSHOT");

        //project with version but not ending in SNAPSHOT
        project2.setVersion("3.5-NEW");

        //creating container Handler for two
        ContainerHandler handler1 = new ContainerHandler(project1, envVarHandler, probeHandler);
        ContainerHandler handler2 = new ContainerHandler(project2, envVarHandler, probeHandler);

        //project without version
        ContainerHandler handler3 = new ContainerHandler(project, envVarHandler, probeHandler);

        images.clear();
        images.add(imageConfiguration1);

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

        ContainerHandler handler = new ContainerHandler(project, envVarHandler, probeHandler);

        //Image Configuration with name and without registry
        ImageConfiguration imageConfiguration2 = new ImageConfiguration.Builder().
                name("test").alias("test-app").buildConfig(buildImageConfiguration1).build();

        //Image Configuration without name and with registry
        ImageConfiguration imageConfiguration3 = new ImageConfiguration.Builder().
                alias("test-app").buildConfig(buildImageConfiguration1).registry("docker.io").build();

        //Image Configuration without name and registry
        ImageConfiguration imageConfiguration4 = new ImageConfiguration.Builder().
                alias("test-app").buildConfig(buildImageConfiguration1).build();

        images.clear();
        images.add(imageConfiguration1);
        images.add(imageConfiguration2);
        images.add(imageConfiguration3);
        images.add(imageConfiguration4);

        containers = handler.getContainers(config1, images);

        assertEquals("docker.io/test",containers.get(0).getImage());
        assertEquals("test",containers.get(1).getImage());
        assertNull(containers.get(2).getImage());
        assertNull(containers.get(3).getImage());
    }

    @Test
    public void getVolumeMountWithoutMountTest() {
        ContainerHandler handler = new ContainerHandler(project, envVarHandler, probeHandler);

        images.clear();
        images.add(imageConfiguration1);

        //volume config without mount
        VolumeConfig volumeConfig1 = new VolumeConfig.Builder().name("first").build();
        volumes1.add(volumeConfig1);
        ResourceConfig config1 = new ResourceConfig.Builder().volumes(volumes1).build();
        containers = handler.getContainers(config1, images);
        assertTrue(containers.get(0).getVolumeMounts().isEmpty());
    }

    @Test
    public void getVolumeMountWithoutNameTest() {

        ContainerHandler handler = new ContainerHandler(project, envVarHandler, probeHandler);

        images.clear();
        images.add(imageConfiguration1);

        List<String> mounts = new ArrayList<>();
        mounts.add("/path/etc");

        //volume config without name but with mount
        VolumeConfig volumeConfig2 = new VolumeConfig.Builder().mounts(mounts).build();
        volumes1.clear();
        volumes1.add(volumeConfig2);

        ResourceConfig config2 = new ResourceConfig.Builder().volumes(volumes1).build();
        containers = handler.getContainers(config2, images);
        assertEquals(1, containers.get(0).getVolumeMounts().size());
        assertEquals(null, containers.get(0).getVolumeMounts().get(0).getName());
        assertEquals("/path/etc", containers.get(0).getVolumeMounts().get(0).getMountPath());
    }

    @Test
    public void getVolumeMountWithNameAndMountTest() {
        ContainerHandler handler = new ContainerHandler(project, envVarHandler, probeHandler);

        List<String> mounts = new ArrayList<>();
        mounts.add("/path/etc");

        images.clear();
        images.add(imageConfiguration1);

        //volume config with name and single mount
        VolumeConfig volumeConfig3 = new VolumeConfig.Builder().name("third").mounts(mounts).build();
        volumes1.clear();
        volumes1.add(volumeConfig3);
        ResourceConfig config3 = new ResourceConfig.Builder().volumes(volumes1).build();
        containers = handler.getContainers(config3, images);
        assertEquals(1, containers.get(0).getVolumeMounts().size());
        assertEquals("third", containers.get(0).getVolumeMounts().get(0).getName());
        assertEquals("/path/etc", containers.get(0).getVolumeMounts().get(0).getMountPath());
    }

    @Test
    public void getVolumeMountWithMultipleMountTest() {
        ContainerHandler handler = new ContainerHandler(project, envVarHandler, probeHandler);

        images.clear();
        images.add(imageConfiguration1);

        List<String> mounts = new ArrayList<>();
        mounts.add("/path/etc");

        //volume config with name and multiple mount
        mounts.add("/path/system");
        mounts.add("/path/sys");
        VolumeConfig volumeConfig4 = new VolumeConfig.Builder().name("test").mounts(mounts).build();
        volumes1.clear();
        volumes1.add(volumeConfig4);
        ResourceConfig config4 = new ResourceConfig.Builder().volumes(volumes1).build();
        containers = handler.getContainers(config4, images);
        assertEquals(3, containers.get(0).getVolumeMounts().size());
        for (int i = 0; i <= 2; i++)
            assertEquals("test", containers.get(0).getVolumeMounts().get(i).getName());
    }

    @Test
    public void getVolumeMountWithEmptyVolumeTest() {
        ContainerHandler handler = new ContainerHandler(project, envVarHandler, probeHandler);

        images.clear();
        images.add(imageConfiguration1);

        //empty volume
        ResourceConfig config5 = new ResourceConfig.Builder().volumes(volumes2).build();
        containers = handler.getContainers(config5, images);
        assertTrue(containers.get(0).getVolumeMounts().isEmpty());
    }

    @Test
    public void containerEmptyPortsTest() {
        ContainerHandler handler = new ContainerHandler(project, envVarHandler, probeHandler);

        images.clear();
        images.add(imageConfiguration1);

        //Empty Ports
        containers = handler.getContainers(config, images);
        assertTrue(containers.get(0).getPorts().isEmpty());
    }

    @Test
    public void containerPortsWithoutPortTest() {

        ContainerHandler handler = new ContainerHandler(project,envVarHandler,probeHandler);

        //without Ports
        BuildImageConfiguration buildImageConfiguration2 = new BuildImageConfiguration.Builder().
                from("fabric8/maven:latest").cleanup("try").compression("gzip").build();

        ImageConfiguration imageConfiguration2 = new ImageConfiguration.Builder().
                name("test").alias("test-app").buildConfig(buildImageConfiguration2).registry("docker.io").build();

        images.clear();
        images.add(imageConfiguration2);

        containers = handler.getContainers(config, images);
        assertTrue(containers.get(0).getPorts().isEmpty());
    }

    @Test
    public void containerPortsWithDifferentPortTest(){
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

        ContainerHandler handler = new ContainerHandler(project,envVarHandler,probeHandler);

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