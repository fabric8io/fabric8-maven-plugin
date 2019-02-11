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
package io.fabric8.maven.core.service;

import io.fabric8.maven.core.access.ClusterAccess;
import io.fabric8.maven.core.config.RuntimeMode;
import io.fabric8.maven.core.service.kubernetes.DockerBuildService;
import io.fabric8.maven.core.service.openshift.OpenshiftBuildService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.openshift.client.OpenShiftClient;
import mockit.Expectations;
import mockit.Mocked;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class Fabric8ServiceHubTest {

    @Mocked
    private Logger logger;

    @Mocked
    private ClusterAccess clusterAccess;

    @Mocked
    private OpenShiftClient openShiftClient;

    @Mocked
    private ServiceHub dockerServiceHub;

    @Mocked
    private BuildService.BuildServiceConfig buildServiceConfig;

    @Mocked
    private MavenProject mavenProject;

    @Mocked
    private RepositorySystem repositorySystem;

    @Before
    public void init() throws Exception {
        new Expectations() {{
            clusterAccess.resolveRuntimeMode(RuntimeMode.kubernetes, withInstanceOf(Logger.class));
            result = RuntimeMode.kubernetes;
            minTimes = 0;

            clusterAccess.resolveRuntimeMode(RuntimeMode.openshift, withInstanceOf(Logger.class));
            result = RuntimeMode.openshift;
            minTimes = 0;

            clusterAccess.resolveRuntimeMode(RuntimeMode.auto, withInstanceOf(Logger.class));
            result = RuntimeMode.kubernetes;
            minTimes = 0;

            clusterAccess.createKubernetesClient();
            result = openShiftClient;
            minTimes = 0;
        }};
    }

    @Test(expected = NullPointerException.class)
    public void testMissingClusterAccess() {
        new Fabric8ServiceHub.Builder()
                .log(logger)
                .build();
    }

    @Test(expected = NullPointerException.class)
    public void testMissingLogger() {
        new Fabric8ServiceHub.Builder()
                .clusterAccess(clusterAccess)
                .build();
    }

    @Test
    public void testBasicInit() {
        new Fabric8ServiceHub.Builder()
                .clusterAccess(clusterAccess)
                .log(logger)
                .platformMode(RuntimeMode.auto)
                .build();
    }

    @Test
    public void testObtainBuildService() {
        Fabric8ServiceHub hub = new Fabric8ServiceHub.Builder()
                .clusterAccess(clusterAccess)
                .log(logger)
                .platformMode(RuntimeMode.kubernetes)
                .dockerServiceHub(dockerServiceHub)
                .buildServiceConfig(buildServiceConfig)
                .build();

        BuildService buildService = hub.getBuildService();

        assertNotNull(buildService);
        assertTrue(buildService instanceof DockerBuildService);
    }

    @Test
    public void testObtainOpenshiftBuildService() {
        Fabric8ServiceHub hub = new Fabric8ServiceHub.Builder()
                .clusterAccess(clusterAccess)
                .log(logger)
                .platformMode(RuntimeMode.openshift)
                .dockerServiceHub(dockerServiceHub)
                .buildServiceConfig(buildServiceConfig)
                .build();

        BuildService buildService = hub.getBuildService();

        assertNotNull(buildService);
        assertTrue(buildService instanceof OpenshiftBuildService);
    }

    @Test
    public void testObtainArtifactResolverService() {
        Fabric8ServiceHub hub = new Fabric8ServiceHub.Builder()
                .clusterAccess(clusterAccess)
                .log(logger)
                .platformMode(RuntimeMode.kubernetes)
                .mavenProject(mavenProject)
                .repositorySystem(repositorySystem)
                .build();

        assertNotNull(hub.getArtifactResolverService());
    }
}
