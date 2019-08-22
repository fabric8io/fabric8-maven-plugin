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

import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.core.service.kubernetes.BuildahIntegration.BuildahBuildService;
import io.fabric8.maven.core.service.kubernetes.BuildahIntegration.BuildahBuildServiceUtil;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.RegistryService;
import io.fabric8.maven.docker.util.AuthConfigFactory;
import io.fabric8.maven.docker.util.Logger;
import mockit.Expectations;
import mockit.Mocked;
import mockit.Tested;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;

public class BuildahBuildServiceTest {

    @Tested
    private BuildahBuildService buildahBuildahService;

    @Tested
    private BuildahBuildServiceUtil buildahBuildServiceUtil;

    @Mocked
    private Logger log;

    @Mocked
    private BuildService.BuildServiceConfig config;

    @Mocked
    private ImageConfiguration imageConfiguration;

    @Mocked
    private AuthConfigFactory authConfigFactory;

    @Test
    public void testSuccesfulBuild() throws Exception {

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
                .labels(new HashMap<String, String>() {{
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

        final io.fabric8.maven.docker.service.BuildService.BuildContext dockerBuildContext = new io.fabric8.maven.docker.service.BuildService.BuildContext.Builder()
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

            config.getBuildDirectory();
            result = "target/test-files/buildah-build-service";

        }};

        buildahBuildahService = new BuildahBuildService(config, log);
        buildahBuildahService.build(imageConfiguration);
    }
}
