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

import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.config.VolumeConfig;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.kubernetes.api.model.Job;
import mockit.Mocked;
import org.apache.maven.project.MavenProject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class JobHandlerTest {

    @Mocked
    EnvVarHandler envVarHandler;

    @Mocked
    ProbeHandler probeHandler;

    @Test
    public void jobHandlerTest() {

        MavenProject project = new MavenProject();

        ContainerHandler containerHandler =
                new ContainerHandler(project, envVarHandler, probeHandler);

        PodTemplateHandler podTemplateHandler = new PodTemplateHandler(containerHandler);

        JobHandler jobHandler = new JobHandler(podTemplateHandler);

        List<String> mounts = new ArrayList<>();
        List<VolumeConfig> volumes1 = new ArrayList<>();

        //volume config with name and multiple mount
        mounts.add("/path/system");
        mounts.add("/path/sys");

        VolumeConfig volumeConfig1 = new VolumeConfig.Builder().name("test")
                .mounts(mounts).type("hostPath").path("/test/path").build();
        volumes1.clear();
        volumes1.add(volumeConfig1);

        ResourceConfig config = new ResourceConfig.Builder()
                .imagePullPolicy("IfNotPresent")
                .controllerName("testing")
                .withServiceAccount("test-account")
                .volumes(volumes1)
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
                name("test").alias("test-app").buildConfig(buildImageConfiguration)
                .registry("docker.io").build();

        List<ImageConfiguration> images = new ArrayList<>();
        images.add(imageConfiguration);

        Job job = jobHandler.getJob(config,images);

        //Assertion
        assertNotNull(job.getSpec());
        assertNotNull(job.getMetadata());
        assertNotNull(job.getSpec().getTemplate());
        assertEquals("testing",job.getMetadata().getName());
        assertEquals("test-account",job.getSpec().getTemplate()
                .getSpec().getServiceAccountName());
        assertFalse(job.getSpec().getTemplate().getSpec().getVolumes().isEmpty());
        assertEquals("test",job.getSpec().getTemplate().getSpec().
                getVolumes().get(0).getName());
        assertEquals("/test/path",job.getSpec().getTemplate()
                .getSpec().getVolumes().get(0).getHostPath().getPath());
        assertNotNull(job.getSpec().getTemplate().getSpec().getContainers());

    }

    @Test
    //invalid controller name
    public void daemonTemplateHandlerSecondTest() {
        try {
            MavenProject project = new MavenProject();

            ContainerHandler containerHandler =
                    new ContainerHandler(project, envVarHandler, probeHandler);

            PodTemplateHandler podTemplateHandler = new PodTemplateHandler(containerHandler);

            JobHandler jobHandler = new JobHandler(podTemplateHandler);

            List<String> mounts = new ArrayList<>();
            List<VolumeConfig> volumes1 = new ArrayList<>();

            //volume config with name and multiple mount
            mounts.add("/path/system");
            mounts.add("/path/sys");

            VolumeConfig volumeConfig1 = new VolumeConfig.Builder()
                    .name("test").mounts(mounts).type("hostPath").path("/test/path").build();
            volumes1.clear();
            volumes1.add(volumeConfig1);

            //with invalid controller name
            ResourceConfig config = new ResourceConfig.Builder()
                    .imagePullPolicy("IfNotPresent")
                    .controllerName("TesTing")
                    .withServiceAccount("test-account")
                    .volumes(volumes1)
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
                    name("test").alias("test-app").buildConfig(buildImageConfiguration)
                    .registry("docker.io").build();

            List<ImageConfiguration> images = new ArrayList<>();
            images.add(imageConfiguration);

            jobHandler.getJob(config, images);
        }
        //asserting the exact message because
        // it throws the same exception in case controller name is null
        catch(IllegalArgumentException exception) {
            assertEquals("Invalid upper case letter 'T' at index 0 for " +
                    "controller name value: TesTing", exception.getMessage());
        }
    }

    @Test
    //without controller name
    public void daemonTemplateHandlerThirdTest() {
        try {
            MavenProject project = new MavenProject();

            ContainerHandler containerHandler = new
                    ContainerHandler(project, envVarHandler, probeHandler);

            PodTemplateHandler podTemplateHandler = new PodTemplateHandler(containerHandler);

            JobHandler jobHandler = new JobHandler(podTemplateHandler);

            List<String> mounts = new ArrayList<>();
            List<VolumeConfig> volumes1 = new ArrayList<>();

            //volume config with name and multiple mount
            mounts.add("/path/system");
            mounts.add("/path/sys");

            VolumeConfig volumeConfig1 = new VolumeConfig.Builder()
                    .name("test").mounts(mounts).type("hostPath").path("/test/path").build();
            volumes1.clear();
            volumes1.add(volumeConfig1);

            //without controller name
            ResourceConfig config = new ResourceConfig.Builder()
                    .imagePullPolicy("IfNotPresent")
                    .withServiceAccount("test-account")
                    .volumes(volumes1)
                    .build();

            List<String> ports = new ArrayList<>();
            ports.add("8080");
            ports.add("9090");

            List<String> tags = new ArrayList<>();
            tags.add("latest");
            tags.add("test");

            //container name with alias
            BuildImageConfiguration buildImageConfiguration = new BuildImageConfiguration.Builder().
                    ports(ports).from("fabric8/maven:latest").cleanup("try").tags(tags)
                    .compression("gzip").build();

            ImageConfiguration imageConfiguration = new ImageConfiguration.Builder().
                    name("test").alias("test-app").buildConfig(buildImageConfiguration)
                    .registry("docker.io").build();

            List<ImageConfiguration> images = new ArrayList<>();
            images.add(imageConfiguration);

            jobHandler.getJob(config, images);
        }
        //asserting the exact message because
        //it throws the same exception in case controller name is invalid
        catch(IllegalArgumentException exception) {
            assertEquals("No controller name is specified!", exception.getMessage());
        }
    }
}