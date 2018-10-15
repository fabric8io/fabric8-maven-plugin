/**
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

import io.fabric8.maven.core.model.GroupArtifactVersion;
import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.config.VolumeConfig;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import mockit.Mocked;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PodTemplateHandlerTest {

    @Mocked
    ProbeHandler probeHandler;

    MavenProject project = new MavenProject();

    List<String> mounts = new ArrayList<>();
    List<VolumeConfig> volumes1 = new ArrayList<>();

    List<ImageConfiguration> images = new ArrayList<>();

    List<String> ports = new ArrayList<>();

    List<String> tags = new ArrayList<>();

    @Before
    public void before(){

        //volume config with name and multiple mount
        mounts.add("/path/system");
        mounts.add("/path/sys");

        ports.add("8080");
        ports.add("9090");

        tags.add("latest");
        tags.add("test");

        //container name with alias
        BuildImageConfiguration buildImageConfiguration = new BuildImageConfiguration.Builder().
                ports(ports).from("fabric8/maven:latest").cleanup("try")
                .tags(tags).compression("gzip").build();

        ImageConfiguration imageConfiguration = new ImageConfiguration.Builder().
                name("test").alias("test-app").buildConfig(buildImageConfiguration)
                .registry("docker.io").build();

        images.add(imageConfiguration);
    }

    @Test
    public void podWithoutVolumeTemplateHandlerTest() {

        ContainerHandler containerHandler = getContainerHandler();

        PodTemplateHandler podTemplateHandler = new PodTemplateHandler(containerHandler);

        //Pod without Volume Config
        ResourceConfig config = new ResourceConfig.Builder()
                .imagePullPolicy("IfNotPresent")
                .controllerName("testing")
                .withServiceAccount("test-account")
                .withReplicas(5)
                .build();

        PodTemplateSpec podTemplateSpec = podTemplateHandler.getPodTemplate(config, images);

        //Assertion
        assertEquals("test-account", podTemplateSpec.getSpec().getServiceAccountName());
        assertTrue(podTemplateSpec.getSpec().getVolumes().isEmpty());
        assertNotNull(podTemplateSpec.getSpec().getContainers());
        assertEquals("test-app", podTemplateSpec.getSpec()
                .getContainers().get(0).getName());
        assertEquals("docker.io/test:latest", podTemplateSpec.getSpec()
                .getContainers().get(0).getImage());
        assertEquals("IfNotPresent", podTemplateSpec.getSpec()
                .getContainers().get(0).getImagePullPolicy());
    }

    private ContainerHandler getContainerHandler() {
        return new ContainerHandler(project.getProperties(), new GroupArtifactVersion("g","a","v"), probeHandler);
    }

    @Test
    public void podWithEmotyVolumeTemplateHandlerTest(){
        ContainerHandler containerHandler = getContainerHandler();

        PodTemplateHandler podTemplateHandler = new PodTemplateHandler(containerHandler);
        //Pod with empty Volume Config and wihtout ServiceAccount
        ResourceConfig config = new ResourceConfig.Builder()
                .imagePullPolicy("IfNotPresent")
                .controllerName("testing")
                .withReplicas(5)
                .volumes(volumes1)
                .build();

        PodTemplateSpec podTemplateSpec = podTemplateHandler.getPodTemplate(config,images);

        //Assertion
        assertNull(podTemplateSpec.getSpec().getServiceAccountName());
        assertTrue(podTemplateSpec.getSpec().getVolumes().isEmpty());
        assertNotNull(podTemplateSpec.getSpec().getContainers());
    }

    @Test
    public void podWithVolumeTemplateHandlerTest(){
        ContainerHandler containerHandler = getContainerHandler();

        PodTemplateHandler podTemplateHandler = new PodTemplateHandler(containerHandler);
        //Config with Volume Config and ServiceAccount
        //valid type
        VolumeConfig volumeConfig1 = new VolumeConfig.Builder().name("test")
                .mounts(mounts).type("hostPath").path("/test/path").build();
        volumes1.clear();
        volumes1.add(volumeConfig1);

        ResourceConfig config = new ResourceConfig.Builder()
                .imagePullPolicy("IfNotPresent")
                .controllerName("testing")
                .withServiceAccount("test-account")
                .withReplicas(5)
                .volumes(volumes1)
                .build();

        PodTemplateSpec podTemplateSpec = podTemplateHandler.getPodTemplate(config,images);

        //Assertion
        assertEquals("test-account",podTemplateSpec.getSpec().getServiceAccountName());
        assertFalse(podTemplateSpec.getSpec().getVolumes().isEmpty());
        assertEquals("test",podTemplateSpec.getSpec()
                .getVolumes().get(0).getName());
        assertEquals("/test/path",podTemplateSpec.getSpec()
                .getVolumes().get(0).getHostPath().getPath());
        assertNotNull(podTemplateSpec.getSpec().getContainers());
    }

    @Test
    public void podWithInvalidVolumeTypeTemplateHandlerTest(){
        ContainerHandler containerHandler = getContainerHandler();

        PodTemplateHandler podTemplateHandler = new PodTemplateHandler(containerHandler);

        //invalid type
        VolumeConfig volumeConfig1 = new VolumeConfig.Builder().name("test")
                .mounts(mounts).type("hoStPath").path("/test/path").build();
        volumes1.clear();
        volumes1.add(volumeConfig1);

        ResourceConfig config = new ResourceConfig.Builder()
                .imagePullPolicy("IfNotPresent")
                .controllerName("testing")
                .withServiceAccount("test-account")
                .withReplicas(5)
                .volumes(volumes1)
                .build();

        PodTemplateSpec podTemplateSpec = podTemplateHandler.getPodTemplate(config,images);

        //Assertion
        assertEquals("test-account",podTemplateSpec.getSpec().getServiceAccountName());
        assertTrue(podTemplateSpec.getSpec().getVolumes().isEmpty());
        assertNotNull(podTemplateSpec.getSpec().getContainers());
    }

    @Test
    public void podWithoutEmptyTypeTemplateHandlerTest(){
        ContainerHandler containerHandler = getContainerHandler();

        PodTemplateHandler podTemplateHandler = new PodTemplateHandler(containerHandler);

        //empty type
        VolumeConfig volumeConfig1 = new VolumeConfig.Builder().name("test").mounts(mounts).build();
        volumes1.clear();
        volumes1.add(volumeConfig1);

        ResourceConfig config = new ResourceConfig.Builder()
                .imagePullPolicy("IfNotPresent")
                .controllerName("testing")
                .withServiceAccount("test-account")
                .withReplicas(5)
                .volumes(volumes1)
                .build();

        PodTemplateSpec podTemplateSpec = podTemplateHandler.getPodTemplate(config,images);

        //Assertion
        assertEquals("test-account",podTemplateSpec.getSpec().getServiceAccountName());
        assertTrue(podTemplateSpec.getSpec().getVolumes().isEmpty());
        assertNotNull(podTemplateSpec.getSpec().getContainers());
    }
}