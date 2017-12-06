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

import io.fabric8.openshift.api.model.Route;
import okhttp3.Response;
import org.apache.http.HttpStatus;
import org.eclipse.jgit.lib.Repository;
import org.json.HTTP;
import org.json.JSONObject;
import org.testng.annotations.Test;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static io.fabric8.kubernetes.assertions.Assertions.assertThat;

public class VertxHealthchecksBooster extends Core {
    public static final String SPRING_BOOT_HTTP_BOOSTER_GIT = "https://github.com/openshiftio-vertx-boosters/vertx-health-checks-booster.git";

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

        // redeploy and assert
        deployAndAssert(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL, EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);
    }

    public void deployAndAssert(Repository testRepository, String buildGoal, String buildProfile) throws Exception {
        String sampleProjectArtifactId = readPomModelFromFile(new File(testRepository.getWorkTree().getAbsolutePath(), RELATIVE_POM_PATH)).getArtifactId();
        runEmbeddedMavenBuild(testRepository, buildGoal, buildProfile);
        waitTillApplicationPodStarts();

        assertThat(openShiftClient).deployment(sampleProjectArtifactId);
        assertThat(openShiftClient).service(sampleProjectArtifactId);

        RouteAssert.assertRoute(openShiftClient, sampleProjectArtifactId);
        testHealthChecks(getApplicationRouteWithName(TESTSUITE_REPOSITORY_ARTIFACT_ID));
    }

    private void testHealthChecks(Route applicationRoute) throws Exception {
        String hostUrl = "http://" + applicationRoute.getSpec().getHost();

        // Check service state
        assert isApplicationUpAndRunning(hostUrl);

        // Stop the service
        assert makeHttpRequest(HttpRequestType.GET, hostUrl + "/api/stop", null).code() == HttpStatus.SC_OK;

        // Check service state after shutdown
        Response serviceStatus = makeHttpRequest(HttpRequestType.GET, hostUrl + "/api/health/liveness", null);
        assert serviceStatus.code() != HttpStatus.SC_OK;
        assert new JSONObject(serviceStatus.body().string()).getString("outcome").equals("DOWN");

        // Wait for recovery
        TimeUnit.SECONDS.sleep(20);

        // Check state again
        assert isApplicationUpAndRunning(hostUrl);
    }

    private boolean isApplicationUpAndRunning(String hostUrl) throws Exception {
        return new JSONObject(makeHttpRequest(HttpRequestType.GET, hostUrl + "/api/health/liveness", null).body().string())
                        .getString("outcome")
                        .equals("UP")
                && new JSONObject(makeHttpRequest(HttpRequestType.GET, hostUrl + "/api/greeting", null).body().string())
                        .getString("content")
                        .equals("Hello, World!");
    }
}
