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

import java.io.IOException;
import org.apache.http.HttpStatus;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static io.fabric8.kubernetes.assertions.Assertions.assertThat;

public class SpringbootHttpBoosterIT extends BaseBoosterIT {

    private final String SPRING_BOOT_HTTP_BOOSTER_BOOSTERYAMLURL = "https://raw.githubusercontent.com/fabric8-launcher/launcher-booster-catalog/master/spring-boot/current-redhat/rest-http/booster.yaml";

    private String SPRING_BOOT_HTTP_BOOSTER_GIT;

    private String RELEASED_VERSION_TAG;

    private final String ANNOTATION_KEY = "testKey", ANNOTATION_VALUE = "testValue";

    private final String FMP_CONFIGURATION_FILE = "/fmp-plugin-config.xml";

    private final String EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL = "fabric8:deploy", EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE = "openshift";

    private final String RELATIVE_POM_PATH = "/pom.xml";

    private final ReadYaml readYaml = new ReadYaml();

    @Before
    public void set_repo_tag() throws IOException{

        BoosterYaml boosterYaml = readYaml.readYaml(SPRING_BOOT_HTTP_BOOSTER_BOOSTERYAMLURL);
        SPRING_BOOT_HTTP_BOOSTER_GIT = boosterYaml.getSource().getGitSource().getUrl();
        RELEASED_VERSION_TAG = boosterYaml.getSource().getGitSource().getRef();

    }

    @Test
    public void deploy_springboot_app_once() throws Exception {

        Repository testRepository = setupSampleTestRepository(SPRING_BOOT_HTTP_BOOSTER_GIT, RELATIVE_POM_PATH, RELEASED_VERSION_TAG);

        deploy(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL, EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);
        waitUntilDeployment(false);
        assertApplication();
    }

    @Test
    public void redeploy_springboot_app() throws Exception {

        Repository testRepository = setupSampleTestRepository(SPRING_BOOT_HTTP_BOOSTER_GIT, RELATIVE_POM_PATH, RELEASED_VERSION_TAG);

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
