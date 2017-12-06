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
import org.junit.Test;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static io.fabric8.kubernetes.assertions.Assertions.assertThat;

public class SpringbootCrudBoosterIT extends Core {
    private static final String SPRING_BOOT_CRUD_BOOSTER_GIT = "https://github.com/snowdrop/spring-boot-crud-booster.git";

    private final String EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL = "fabric8:deploy -DskipTests", EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE = "openshift";

    private final String testEndpoint = "/api/fruits";

    private final String TESTSUITE_DB_NAME = "my-database", TESTSUITE_DB_IMAGE = "openshift/postgresql-92-centos7";

    private final String RELATIVE_POM_PATH = "/pom.xml";

    @Test
    public void deploy_springboot_app_once() throws Exception {
        Repository testRepository = setupSampleTestRepository(SPRING_BOOT_CRUD_BOOSTER_GIT, RELATIVE_POM_PATH);
        deployDatabaseUsingCLI();

        deployAndAssert(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL, EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);
    }

    @Test
    public void redeploy_springboot_app() throws Exception {
        Repository testRepository = setupSampleTestRepository(SPRING_BOOT_CRUD_BOOSTER_GIT, RELATIVE_POM_PATH);

        deployDatabaseUsingCLI();
        deployAndAssert(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL, EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);

        // Make some changes in ConfigMap and rollout
        updateSourceCode(testRepository, RELATIVE_POM_PATH);

        deployAndAssert(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL, EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);
    }

    private void deployAndAssert(Repository testRepository, String buildGoal, String buildProfile) throws Exception {
        String sampleProjectArtifactId = readPomModelFromFile(new File(testRepository.getWorkTree().getAbsolutePath(), RELATIVE_POM_PATH)).getArtifactId();
        runEmbeddedMavenBuild(testRepository, buildGoal, buildProfile);
        // Waiting for application pod to start.
        waitTillApplicationPodStarts();
        // Wait for Services, Route, ConfigMaps to refresh according to the deployment.
        TimeUnit.SECONDS.sleep(20);

        assertThat(openShiftClient).deployment(sampleProjectArtifactId);
        assertThat(openShiftClient).service(sampleProjectArtifactId);

        RouteAssert.assertRoute(openShiftClient, sampleProjectArtifactId);
        executeCRUDAssertions(getApplicationRouteWithName(TESTSUITE_REPOSITORY_ARTIFACT_ID));
    }

    private void executeCRUDAssertions(Route applicationRoute) throws Exception {
        String hostUrl = "http://" + applicationRoute.getSpec().getHost() + testEndpoint;
        JSONObject jsonRequest = new JSONObject();
        String testFruitName = "Pineapple";

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

        // (U) Update
        jsonRequest.put("name", "Strawberry");
        Response updateResponse = makeHttpRequest(HttpRequestType.PUT, hostUrl, jsonRequest.toString());
        Response checkUpdateResponse = makeHttpRequest(HttpRequestType.GET, hostUrl, null);
        String updatedFruitName = new JSONObject(checkUpdateResponse.body().string()).getString("name");
        assert updateResponse.code() == HttpStatus.SC_OK;
        assert updatedFruitName.equals("Strawberry");

        // (D) Delete
        Response deleteResponse = makeHttpRequest(HttpRequestType.DELETE, hostUrl, null);
        assert deleteResponse.code() == HttpStatus.SC_NO_CONTENT;
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
        int processRetval = exec(deployCommand);
        System.out.println("process returned : " + processRetval);
    }
}
