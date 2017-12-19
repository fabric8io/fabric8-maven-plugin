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

import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.config.VolumeConfig;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import mockit.Mocked;
import org.apache.maven.project.MavenProject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class PodTemplateHandlerTest {

    @Mocked
    EnvVarHandler envVarHandler;

    @Mocked
    ProbeHandler probeHandler;

    @Test
    public void podTemplateHandlerTest() {

        MavenProject project = new MavenProject();

        ContainerHandler containerHandler =
                new ContainerHandler(project, envVarHandler, probeHandler);

        PodTemplateHandler podTemplateHandler = new PodTemplateHandler(containerHandler);

        List<String> mounts = new ArrayList<>();
        List<VolumeConfig> volumes1 = new ArrayList<>();

        //volume config with name and multiple mount
        mounts.add("/path/system");
        mounts.add("/path/sys");

        //Pod without Volume Config
        ResourceConfig config = new ResourceConfig.Builder()
                .imagePullPolicy("IfNotPresent")
                .controllerName("testing")
                .withServiceAccount("test-account")
                .withReplicas(5)
                .build();

        List<String> ports = new ArrayList<>();
        ports.add("8080");
        ports.add("9090");

        List<String> tags = new ArrayList<>();
        tags.add("latest");
        tags.add("test");

        //container name with alias
        BuildImageConfiguration buildImageConfiguration = new BuildImageConfiguration.Builder().
                ports(ports).from("fabric8/maven:latest").cleanup("try")
                .tags(tags).compression("gzip").build();

        ImageConfiguration imageConfiguration = new ImageConfiguration.Builder().
                name("test").alias("test-app").buildConfig(buildImageConfiguration).
                registry("docker.io").build();

        List<ImageConfiguration> images = new ArrayList<>();
        images.add(imageConfiguration);

        PodTemplateSpec podTemplateSpec = podTemplateHandler.getPodTemplate(config,images);

        //Assertion
        assertEquals("test-account",podTemplateSpec.getSpec().getServiceAccountName());
        assertTrue(podTemplateSpec.getSpec().getVolumes().isEmpty());
        assertNotNull(podTemplateSpec.getSpec().getContainers());
        assertEquals("test-app",podTemplateSpec.getSpec()
                .getContainers().get(0).getName());
        assertEquals("docker.io/test",podTemplateSpec.getSpec()
                .getContainers().get(0).getImage());
        assertEquals("IfNotPresent",podTemplateSpec.getSpec()
                .getContainers().get(0).getImagePullPolicy());

        //Pod with empty Volume Config and wihtout ServiceAccount
        config = new ResourceConfig.Builder()
                .imagePullPolicy("IfNotPresent")
                .controllerName("testing")
                .withReplicas(5)
                .volumes(volumes1)
                .build();

        podTemplateSpec = podTemplateHandler.getPodTemplate(config,images);

        //Assertion
        assertNull(podTemplateSpec.getSpec().getServiceAccountName());
        assertTrue(podTemplateSpec.getSpec().getVolumes().isEmpty());
        assertNotNull(podTemplateSpec.getSpec().getContainers());

        //Config with Volume Config and ServiceAccount
        //valid type
        VolumeConfig volumeConfig1 = new VolumeConfig.Builder().name("test")
                .mounts(mounts).type("hostPath").path("/test/path").build();
        volumes1.clear();
        volumes1.add(volumeConfig1);

        config = new ResourceConfig.Builder()
                .imagePullPolicy("IfNotPresent")
                .controllerName("testing")
                .withServiceAccount("test-account")
                .withReplicas(5)
                .volumes(volumes1)
                .build();

        podTemplateSpec = podTemplateHandler.getPodTemplate(config,images);

        //Assertion
        assertEquals("test-account",podTemplateSpec.getSpec().getServiceAccountName());
        assertFalse(podTemplateSpec.getSpec().getVolumes().isEmpty());
        assertEquals("test",podTemplateSpec.getSpec()
                .getVolumes().get(0).getName());
        assertEquals("/test/path",podTemplateSpec.getSpec()
                .getVolumes().get(0).getHostPath().getPath());
        assertNotNull(podTemplateSpec.getSpec().getContainers());

        //invalid type
        volumeConfig1 = new VolumeConfig.Builder().name("test")
                .mounts(mounts).type("hoStPath").path("/test/path").build();
        volumes1.clear();
        volumes1.add(volumeConfig1);

        config = new ResourceConfig.Builder()
                .imagePullPolicy("IfNotPresent")
                .controllerName("testing")
                .withServiceAccount("test-account")
                .withReplicas(5)
                .volumes(volumes1)
                .build();

        podTemplateSpec = podTemplateHandler.getPodTemplate(config,images);

        //Assertion
        assertEquals("test-account",podTemplateSpec.getSpec().getServiceAccountName());
        assertTrue(podTemplateSpec.getSpec().getVolumes().isEmpty());
        assertNotNull(podTemplateSpec.getSpec().getContainers());

        //empty type
        volumeConfig1 = new VolumeConfig.Builder().name("test").mounts(mounts).build();
        volumes1.clear();
        volumes1.add(volumeConfig1);

        config = new ResourceConfig.Builder()
                .imagePullPolicy("IfNotPresent")
                .controllerName("testing")
                .withServiceAccount("test-account")
                .withReplicas(5)
                .volumes(volumes1)
                .build();

        podTemplateSpec = podTemplateHandler.getPodTemplate(config,images);

        //Assertion
        assertEquals("test-account",podTemplateSpec.getSpec().getServiceAccountName());
        assertTrue(podTemplateSpec.getSpec().getVolumes().isEmpty());
        assertNotNull(podTemplateSpec.getSpec().getContainers());
    }
}