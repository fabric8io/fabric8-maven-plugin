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
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static io.fabric8.kubernetes.assertions.Assertions.assertThat;

public class SpringbootCrudBoosterIT extends BaseBoosterIT {

    private final String SPRING_BOOT_CRUD_BOOSTER_BOOSTERYAMLURL = "https://raw.githubusercontent.com/fabric8-launcher/launcher-booster-catalog/master/spring-boot/current-redhat/crud/booster.yaml";

    private String SPRING_BOOT_CRUD_BOOSTER_GIT;

    private String RELEASED_VERSION_TAG;

    private final String EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL = "fabric8:deploy -DskipTests", EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE = "openshift";

    private final String TEST_ENDPOINT = "/api/fruits";

    private final String TESTSUITE_DB_NAME = "my-database", TESTSUITE_DB_IMAGE = "openshift/postgresql-92-centos7";

    private final String RELATIVE_POM_PATH = "/pom.xml";

    private final String ANNOTATION_KEY = "springboot-crud-testKey", ANNOTATION_VALUE = "springboot-crud-testValue";

    private final ReadYaml readYaml = new ReadYaml();

    @Before
    public void set_repo_tag() throws IOException {

        BoosterYaml boosterYaml = readYaml.readYaml(SPRING_BOOT_CRUD_BOOSTER_BOOSTERYAMLURL);
        SPRING_BOOT_CRUD_BOOSTER_GIT = boosterYaml.getSource().getGitSource().getUrl();
        RELEASED_VERSION_TAG = boosterYaml.getSource().getGitSource().getRef();

    }

    @Test
    public void deploy_springboot_app_once() throws Exception {

        Repository testRepository = setupSampleTestRepository(SPRING_BOOT_CRUD_BOOSTER_GIT, RELATIVE_POM_PATH, RELEASED_VERSION_TAG);
        deployDatabaseUsingCLI();

        deploy(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL, EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);
        waitAfterDeployment(false);
        assertApplication();
    }

    @Test
    public void redeploy_springboot_app() throws Exception {

        Repository testRepository = setupSampleTestRepository(SPRING_BOOT_CRUD_BOOSTER_GIT, RELATIVE_POM_PATH, RELEASED_VERSION_TAG);
        deployDatabaseUsingCLI();

        // Make some changes in ConfigMap and rollout
        updateSourceCode(testRepository, RELATIVE_POM_PATH);
        addRedeploymentAnnotations(testRepository, RELATIVE_POM_PATH, ANNOTATION_KEY, ANNOTATION_VALUE, fmpConfigurationFile);

        deploy(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL, EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);
        waitAfterDeployment(true);
        assertApplication();
    }

    private void deploy(Repository testRepository, String buildGoal, String buildProfile) throws Exception {
        runEmbeddedMavenBuild(testRepository, buildGoal, buildProfile);
    }

    private void assertApplication() throws Exception {
        assertThat(openShiftClient).deployment(testsuiteRepositoryArtifactId);
        assertThat(openShiftClient).service(testsuiteRepositoryArtifactId);

        RouteAssert.assertRoute(openShiftClient, testsuiteRepositoryArtifactId);
        executeCRUDAssertions(getApplicationRouteWithName(testsuiteRepositoryArtifactId));
    }

    private void waitAfterDeployment(boolean bIsRedeployed) throws Exception {
        // Waiting for application pod to start.
        if (bIsRedeployed)
            waitTillApplicationPodStarts(ANNOTATION_KEY, ANNOTATION_VALUE);
        else
            waitTillApplicationPodStarts();
        // Wait for Services, Route, ConfigMaps to refresh according to the deployment.
        TimeUnit.SECONDS.sleep(20);
    }

    private void executeCRUDAssertions(Route applicationRoute) throws Exception {
        String hostUrl = "http://" + applicationRoute.getSpec().getHost() + TEST_ENDPOINT;
        JSONObject jsonRequest = new JSONObject();
        String testFruitName = "Pineapple";

        // limiting test scope, a basic write followed by read is sufficient.
        // (C) Create
        jsonRequest.put("name", testFruitName);
        Response createResponse = makeHttpRequest(HttpRequestType.POST, hostUrl, jsonRequest.toString());
        assert createResponse.code() == HttpStatus.SC_CREATED;
        Integer fruitId = new JSONObject(createResponse.body().string()).getInt("id");
        hostUrl += ("/" + fruitId.toString());

        // (R) Read
        Response readResponse = makeHttpRequest(HttpRequestType.GET, hostUrl, null);
        String fruitName = new JSONObject(readResponse.body().string()).getString("name");
        assert readResponse.code() == HttpStatus.SC_OK;
        assert fruitName.equals(testFruitName);
    }


    private void deployDatabaseUsingCLI() throws Exception {
        /**
         * Currently kubernetes-client doesn't have any support for oc new-app. So for now
         * doing this.
         */
        String deployCommand = "oc new-app " +
                " -ePOSTGRESQL_USER=luke" +
                " -ePOSTGRESQL_PASSWORD=secret " +
                " -ePOSTGRESQL_DATABASE=my_data " +
                TESTSUITE_DB_IMAGE +
                " --name=" + TESTSUITE_DB_NAME;
        exec(deployCommand);
    }

    @After
    public void cleanup() throws Exception {
        cleanSampleTestRepository();
    }
}
