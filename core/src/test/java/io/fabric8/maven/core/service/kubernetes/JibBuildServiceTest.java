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
package io.fabric8.maven.core.service.kubernetes;

import io.fabric8.maven.core.util.JibBuildServiceUtil;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.BuildService;
import io.fabric8.maven.docker.service.RegistryService;
import io.fabric8.maven.docker.util.AuthConfigFactory;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import mockit.Expectations;
import mockit.Mocked;
import mockit.Tested;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;

public class JibBuildServiceTest {

    @Tested
    private JibBuildService jibBuildService;

    @Tested
    private JibBuildServiceUtil jibBuildServiceUtil;

    @Mocked
    private Logger logger;

    @Mocked
    private io.fabric8.maven.core.service.BuildService.BuildServiceConfig config;

    @Mocked
    private ImageConfiguration imageConfiguration;

    @Mocked
    private AuthConfigFactory authConfigFactory;

    @Test
    public void testSuccessfulBuild() throws Exception {

        //Preparation Code For Testing The Class

        MojoParameters mojoParameters = new MojoParameters(null, null, null, null, null, null, null, "target/docker", null);
        final String imageName = "image-name";

        AssemblyConfiguration assemblyConfiguration = new AssemblyConfiguration.Builder()
                .targetDir("/deployments")
                .build();

        BuildImageConfiguration buildImageConfiguration = new BuildImageConfiguration.Builder()
                .from("fabric8/java-centos-openjdk8-jdk:1.5")
                .env(new HashMap<String, String>() {{
                    put("john", "doe");
                    put("foo", "bar");
                }})
                .ports(new ArrayList<String>() {{
                    add("80");
                    add("443");
                }})
                .entryPoint(null)
                .assembly(assemblyConfiguration)
                .build();

        final BuildService.BuildContext dockerBuildContext = new BuildService.BuildContext.Builder()
                .registryConfig(new RegistryService.RegistryConfig.Builder()
                        .authConfigFactory(authConfigFactory)
                        .build())
                .build();

        new Expectations() {{
            imageConfiguration.getBuildConfiguration();
            result = buildImageConfiguration;

            imageConfiguration.getName();
            result = imageName;

            config.getDockerBuildContext();
            result = dockerBuildContext;

            config.getDockerMojoParameters();
            result = mojoParameters;

            config.getBuildDirectory();
            result = "target/test-files/jib-build-service";

        }};

        //Code To Be Tested
        jibBuildService = new JibBuildService(config, logger);
        jibBuildService.build(imageConfiguration);
    }
}