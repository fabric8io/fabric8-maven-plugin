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

package io.fabric8.maven.rt;

import io.fabric8.openshift.api.model.*;
import org.apache.http.HttpStatus;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

import static io.fabric8.kubernetes.assertions.Assertions.assertThat;

/**
 * Created by hshinde on 11/23/17.
 */
public class SpringbootHttpBoosterIT extends BaseBoosterIT {

    private final String SPRING_BOOT_HTTP_BOOSTER_GIT = "https://github.com/snowdrop/spring-boot-http-booster.git";

    private final String ANNOTATION_KEY = "testKey", ANNOTATION_VALUE = "testValue";

    private final String FMP_CONFIGURATION_FILE = "/fmp-plugin-config.xml";

    private final String EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL = "fabric8:deploy -Dfabric8.openshift.trimImageInContainerSpec=true", EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE = "openshift";

    private final String RELATIVE_POM_PATH = "/pom.xml";

    @Test
    public void deploy_springboot_app_once() throws Exception {
        Repository testRepository = setupSampleTestRepository(SPRING_BOOT_HTTP_BOOSTER_GIT, RELATIVE_POM_PATH);

        addRedeploymentAnnotations(testRepository, RELATIVE_POM_PATH, "deploymentType", "deployOnce", fmpConfigurationFile);

        deploy(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL, EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);
        waitTillApplicationPodStarts("deploymentType", "deployOnce");
        TimeUnit.SECONDS.sleep(20);
        assertApplication();
    }

    @Test
    public void redeploy_springboot_app() throws Exception {
        Repository testRepository = setupSampleTestRepository(SPRING_BOOT_HTTP_BOOSTER_GIT, RELATIVE_POM_PATH);

        // change the source code
        updateSourceCode(testRepository, RELATIVE_POM_PATH);
        addRedeploymentAnnotations(testRepository, RELATIVE_POM_PATH, ANNOTATION_KEY, ANNOTATION_VALUE, FMP_CONFIGURATION_FILE);

        // redeploy and assert
        deploy(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL, EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);
        waitUntilDeployment(true);
        assertApplication();
        assert checkDeploymentsForAnnotation(ANNOTATION_KEY);
    }

    private void deploy(Repository testRepository, String buildGoal, String buildProfile) throws Exception {
        runEmbeddedMavenBuild(testRepository, buildGoal, buildProfile);
    }

    private void assertApplication() throws Exception {
        assertThat(openShiftClient).deployment(testsuiteRepositoryArtifactId);
        assertThat(openShiftClient).service(testsuiteRepositoryArtifactId);
        RouteAssert.assertRoute(openShiftClient, testsuiteRepositoryArtifactId);
        assertApplicationPodRoute(getApplicationRouteWithName(testsuiteRepositoryArtifactId));
    }

    private void waitUntilDeployment(boolean bIsReployed) throws Exception {
        if(bIsReployed)
            waitTillApplicationPodStarts(ANNOTATION_KEY, ANNOTATION_VALUE);
        else
            waitTillApplicationPodStarts();
    }

    /**
     * Fetches Route information corresponding to the application pod and checks whether the
     * endpoint is a valid url or not.
     *
     * @throws Exception
     */
    private void assertApplicationPodRoute(Route applicationRoute) throws Exception {
        String hostRoute = null;
        if (applicationRoute != null) {
            hostRoute = applicationRoute.getSpec().getHost();
            if (hostRoute != null) {
                assert makeHttpRequest(HttpRequestType.GET,"http://" + hostRoute, null).code() == HttpStatus.SC_OK;;
            }
        } else {
            throw new AssertionError("[No route found for: " + testsuiteRepositoryArtifactId + "]\n");
        }
    }

    @After
    public void cleanup() throws Exception {
        cleanSampleTestRepository();
    }
}
