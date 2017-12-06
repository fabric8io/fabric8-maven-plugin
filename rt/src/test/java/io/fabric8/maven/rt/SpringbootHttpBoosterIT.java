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

import java.io.File;

import static io.fabric8.kubernetes.assertions.Assertions.assertThat;


/**
 * Created by hshinde on 11/23/17.
 */

public class SpringbootHttpBoosterIT extends Core {

    public static final String SPRING_BOOT_HTTP_BOOSTER_GIT = "https://github.com/snowdrop/spring-boot-http-booster.git";


    public static final String ANNOTATION_KEY = "testKey", ANNOTATION_VALUE = "testValue";

    public final String FMP_CONFIGURATION_FILE = "/fmp-plugin-config.xml";

    public final String EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL = "fabric8:deploy", EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE = "openshift";

    private final String RELATIVE_POM_PATH = "/pom.xml";

    @Test
    public void deploy_springboot_app_once() throws Exception {
        Repository testRepository = setupSampleTestRepository(SPRING_BOOT_HTTP_BOOSTER_GIT, RELATIVE_POM_PATH);

        deployAndAssert(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL, EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);
    }

    @Test
    public void redeploy_springboot_app() throws Exception {
        Repository testRepository = setupSampleTestRepository(SPRING_BOOT_HTTP_BOOSTER_GIT, RELATIVE_POM_PATH);
        deployAndAssert(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL, EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);

        // change the source code
        updateSourceCode(testRepository, RELATIVE_POM_PATH);
        addRedeploymentAnnotations(testRepository, RELATIVE_POM_PATH, ANNOTATION_KEY, ANNOTATION_VALUE, FMP_CONFIGURATION_FILE);

        // redeploy and assert
        deployAndAssert(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL, EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);
        // check for redeployment specific scenario
        assert checkDeploymentsForAnnotation(ANNOTATION_KEY);
        waitForRunningPodAndCheckEndpoints();
    }

    public void deployAndAssert(Repository testRepository, String buildGoal, String buildProfile) throws Exception {
        String sampleProjectArtifactId = readPomModelFromFile(new File(testRepository.getWorkTree().getAbsolutePath(), RELATIVE_POM_PATH)).getArtifactId();
        runEmbeddedMavenBuild(testRepository, buildGoal, buildProfile);

        assertThat(openShiftClient).deployment(sampleProjectArtifactId);
        assertThat(openShiftClient).service(sampleProjectArtifactId);

        RouteAssert.assertRoute(openShiftClient, sampleProjectArtifactId);
    }

    private void waitForRunningPodAndCheckEndpoints() throws Exception {
        waitTillApplicationPodStarts();
        assertApplicationPodRoute();
    }

    /**
     * Fetches Route information corresponding to the application pod and checks whether the
     * endpoint is a valid url or not.
     *
     * @throws Exception
     */
    public void assertApplicationPodRoute() throws Exception {
        if (applicationPod == null)
            throw new AssertionError("No application pod found for this application");

        String hostRoute = null;
        Route applicationRoute = getApplicationRouteWithName(TESTSUITE_REPOSITORY_ARTIFACT_ID);
        if (applicationRoute != null) {
            hostRoute = applicationRoute.getSpec().getHost();
            if (hostRoute != null) {
                assert makeHttpRequest(HttpRequestType.GET,"http://" + hostRoute, null).code() == HttpStatus.SC_OK;;
            }
        } else {
            throw new AssertionError("[No route found for: " + TESTSUITE_REPOSITORY_ARTIFACT_ID + "]\n");
        }
    }

    @After
    public void cleanup() throws Exception {
        cleanSampleTestRepository();
    }
}
