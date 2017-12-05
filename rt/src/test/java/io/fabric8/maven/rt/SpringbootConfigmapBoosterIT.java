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

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.openshift.api.model.RoleBinding;
import io.fabric8.openshift.api.model.RoleBindingBuilder;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteList;
import okhttp3.Response;
import org.eclipse.jgit.lib.Repository;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.fabric8.kubernetes.assertions.Assertions.assertThat;

public class SpringbootConfigmapBoosterIT extends Core {
    public static final String SPRING_BOOT_CONFIGMAP_BOOSTER_GIT = "https://github.com/snowdrop/spring-boot-configmap-booster.git";

    public static final String TESTSUITE_CONFIGMAP_NAME = "app-config";

    public final String EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL = "fabric8:deploy -DskipTests", EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE = "openshift";

    private final String testEndpoint = "/api/greeting";

    private final String RELATIVE_POM_PATH = "/greeting-service/pom.xml";

    @Test
    public void deploy_springboot_app_once() throws Exception {
        Repository testRepository = setupSampleTestRepository(SPRING_BOOT_CONFIGMAP_BOOSTER_GIT, RELATIVE_POM_PATH);

        createViewRoleToServiceAccount();
        createConfigMapResource(TESTSUITE_CONFIGMAP_NAME, "greeting.message: Hello World from a ConfigMap!");
        deployAndAssert(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL, EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);
        // Check if the configmap's properties are accessible by the Application runnning in pod.
        assert assertApplicationEndpoint("content", "Hello World from a ConfigMap!");

        openShiftClient.configMaps().inNamespace(testSuiteNamespace).withName(TESTSUITE_CONFIGMAP_NAME).delete();
    }

    @Test
    public void redeploy_springboot_app() throws Exception {
        Repository testRepository = setupSampleTestRepository(SPRING_BOOT_CONFIGMAP_BOOSTER_GIT, RELATIVE_POM_PATH);

        createViewRoleToServiceAccount();
        // 1. Deployment
        createConfigMapResource(TESTSUITE_CONFIGMAP_NAME, "greeting.message: Hello World from a ConfigMap!");
        deployAndAssert(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL, EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);
        assert assertApplicationEndpoint("content", "Hello World from a ConfigMap!");

        // Make some changes in ConfigMap and rollout
        updateSourceCode(testRepository, RELATIVE_POM_PATH);
        createOrReplaceConfigMap(TESTSUITE_CONFIGMAP_NAME, "greeting.message: Bonjour World from a ConfigMap!");

        // 2. Re-Deployment
        deployAndAssert(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL, EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);
        assert assertApplicationEndpoint("content", "Bonjour World from a ConfigMap!");

        openShiftClient.configMaps().inNamespace(testSuiteNamespace).withName(TESTSUITE_CONFIGMAP_NAME).delete();
    }

    public void deployAndAssert(Repository testRepository, String buildGoal, String buildProfile) throws Exception {
        runEmbeddedMavenBuild(testRepository, buildGoal, buildProfile);
        // Waiting for application pod to start.
        waitTillApplicationPodStarts();
        // Wait for Services, Route, ConfigMaps to refresh according to the deployment.
        TimeUnit.SECONDS.sleep(20);

        assertThat(openShiftClient).deployment(TESTSUITE_REPOSITORY_ARTIFACT_ID);
        assertThat(openShiftClient).service(TESTSUITE_REPOSITORY_ARTIFACT_ID);

        RouteAssert.assertRoute(openShiftClient, TESTSUITE_REPOSITORY_ARTIFACT_ID);
    }

    public void createOrReplaceConfigMap(String name, String sampleApplicationProperty) {
        openShiftClient.configMaps()
                .inNamespace(testSuiteNamespace)
                .withName(name)
                .edit()
                .addToData("application.properties", sampleApplicationProperty)
                .done();
    }

    public void createConfigMapResource(String name, String sampleApplicationProperty) {
        if (openShiftClient.configMaps().inNamespace(testSuiteNamespace).withName(name).get() == null) {
            openShiftClient.configMaps()
                    .inNamespace(testSuiteNamespace)
                    .createNew()
                    .withNewMetadata()
                    .withName(name)
                    .endMetadata()
                    .addToData("application.properties", sampleApplicationProperty)
                    .done();
        }
    }

    private void createViewRoleToServiceAccount() throws Exception {
        exec("oc policy add-role-to-user view -z default");
        /*
        RoleBinding response = openShiftClient.roleBindings().createNew()
                    .withNewMetadata().withName("view").endMetadata()
                    .addToUserNames("default")
                    .addNewSubject().withKind("ServiceAccount").withName("svcscct").endSubject()
                    .done();
        */
    }

    public boolean isCorrectResponseContent(Response response, String key, String value) throws Exception {
        JSONObject jsonResponse = new JSONObject(response.body().string());
        return jsonResponse.getString(key).equals(value);
    }

    public boolean assertApplicationEndpoint(String key, String value) throws Exception {
        Route applicationRoute = getApplicationRouteWithName(TESTSUITE_REPOSITORY_ARTIFACT_ID);
        String hostUrl = applicationRoute.getSpec().getHost() + testEndpoint;
        Response response = makeHttpRequest(HttpRequestType.GET, "http://" + hostUrl, null);
        return isCorrectResponseContent(response, key, value);
    }

    @After
    public void cleanup() throws Exception {
        cleanSampleTestRepository();
    }
}
