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
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.junit.Before;


import static io.fabric8.kubernetes.assertions.Assertions.assertThat;

public class VertxHealthchecksBoosterIT extends BaseBoosterIT {

    private final String VERTX_HEALTHCHECK_BOOSTER_BOOSTERYAMLURL = "https://raw.githubusercontent.com/fabric8-launcher/launcher-booster-catalog/master/vert.x/redhat/health-check/booster.yaml";

    private String VERTX_HEALTHCHECK_BOOSTER_GIT;

    private String RELEASED_VERSION_TAG;

    private final String EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL = "fabric8:deploy", EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE = "openshift";

    private final String RELATIVE_POM_PATH = "/pom.xml";

    private final String ANNOTATION_KEY = "vertx-healthcheck-testKey", ANNOTATION_VALUE = "vertx-healthcheck-testValue";

    private final ReadYaml readYaml = new ReadYaml();

    @Before
    public void set_repo_tag() throws IOException {

        BoosterYaml boosterYaml = readYaml.readYaml(VERTX_HEALTHCHECK_BOOSTER_BOOSTERYAMLURL);
        VERTX_HEALTHCHECK_BOOSTER_GIT = boosterYaml.getSource().getGitSource().getUrl();
        RELEASED_VERSION_TAG = boosterYaml.getEnvironment().getProduction().getSource().getGitSource().getRef();

    }

    @Test
    public void deploy_vertx_app_once() throws Exception {

        Repository testRepository = setupSampleTestRepository(VERTX_HEALTHCHECK_BOOSTER_GIT, RELATIVE_POM_PATH, RELEASED_VERSION_TAG);

        deploy(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL, EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);
        waitAfterDeployment(false);
        assertDeployment();
    }

    @Test
    public void redeploy_vertx_app() throws Exception {

        Repository testRepository = setupSampleTestRepository(VERTX_HEALTHCHECK_BOOSTER_GIT, RELATIVE_POM_PATH, RELEASED_VERSION_TAG);

        // change the source code
        updateSourceCode(testRepository, RELATIVE_POM_PATH);
        addRedeploymentAnnotations(testRepository, RELATIVE_POM_PATH, ANNOTATION_KEY, ANNOTATION_VALUE, fmpConfigurationFile);

        // redeploy and assert
        deploy(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL, EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);
        waitAfterDeployment(true);
        assertDeployment();
    }

    private void deploy(Repository testRepository, String buildGoal, String buildProfile) throws Exception {
        runEmbeddedMavenBuild(testRepository, buildGoal, buildProfile);
        waitTillApplicationPodStarts();
    }

    private void waitAfterDeployment(boolean bIsRedeployed) throws Exception {
        // Waiting for application pod to start.
        if(bIsRedeployed)
            waitTillApplicationPodStarts(ANNOTATION_KEY, ANNOTATION_VALUE);
        else
            waitTillApplicationPodStarts();
    }

    private void assertDeployment() throws Exception {
        assertThat(openShiftClient).deployment(testsuiteRepositoryArtifactId);
        assertThat(openShiftClient).service(testsuiteRepositoryArtifactId);

        RouteAssert.assertRoute(openShiftClient, testsuiteRepositoryArtifactId);
        testHealthChecks(getApplicationRouteWithName(testsuiteRepositoryArtifactId));
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
        assertApplicationRecovery(hostUrl + "/api/greeting", 120);
    }

    /**
     * Await for at most `awaitTimeInSeconds` to see if the application is able to recover
     * on its own or not.
     *
     * @param hostUrl
     * @throws Exception
     */
    private void assertApplicationRecovery(String hostUrl, long awaitTimeInSeconds) throws Exception {
        for(int nSeconds = 0; nSeconds < awaitTimeInSeconds; nSeconds++) {
            Response serviceResponse = makeHttpRequest(HttpRequestType.GET, hostUrl, null);
            if(serviceResponse.code() == HttpStatus.SC_OK) {
                logger.info("Application recovery successful");
                return;
            }
            TimeUnit.SECONDS.sleep(1);
        }
        throw new AssertionError("Application recovery failed");
    }

    private boolean isApplicationUpAndRunning(String hostUrl) throws Exception {
        return new JSONObject(makeHttpRequest(HttpRequestType.GET, hostUrl + "/api/health/liveness", null).body().string())
                        .getString("outcome")
                        .equals("UP")
                && new JSONObject(makeHttpRequest(HttpRequestType.GET, hostUrl + "/api/greeting", null).body().string())
                        .getString("content")
                        .equals("Hello, World!");
    }

    @After
    public void cleanup() throws Exception {
        cleanSampleTestRepository();
    }
}
