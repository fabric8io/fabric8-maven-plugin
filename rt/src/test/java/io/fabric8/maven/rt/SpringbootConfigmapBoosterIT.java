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
import org.eclipse.jgit.lib.Repository;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.fabric8.kubernetes.assertions.Assertions.assertThat;

public class SpringbootConfigmapBoosterIT extends BaseBoosterIT {

    private final String SPRING_BOOT_CONFIGMAP_BOOSTER_BOOSTERYAMLURL = "https://raw.githubusercontent.com/fabric8-launcher/launcher-booster-catalog/master/spring-boot/current-redhat/configmap/booster.yaml";

    private String SPRING_BOOT_CONFIGMAP_BOOSTER_GIT;

    private String RELEASED_VERSION_TAG;

    private final String TESTSUITE_CONFIGMAP_NAME = "app-config";

    private final String EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL = "fabric8:deploy -DskipTests -Dfabric8-maven-plugin.version=" , EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE = "openshift";

    private final String TEST_ENDPOINT = "/api/greeting";

    private final String RELATIVE_POM_PATH = "/pom.xml";

    private final String ANNOTATION_KEY = "springboot-configmap-testKey", ANNOTATION_VALUE = "springboot-configmap-testValue";

    private final ReadYaml readYaml = new ReadYaml();

    @Before
    public void set_repo_tag() throws IOException {

        BoosterYaml boosterYaml = readYaml.readYaml(SPRING_BOOT_CONFIGMAP_BOOSTER_BOOSTERYAMLURL);
        SPRING_BOOT_CONFIGMAP_BOOSTER_GIT = boosterYaml.getSource().getGitSource().getUrl();
        RELEASED_VERSION_TAG = boosterYaml.getSource().getGitSource().getRef();

    }

    @Test
    public void deploy_springboot_app_once() throws Exception {


        Repository testRepository = setupSampleTestRepository(SPRING_BOOT_CONFIGMAP_BOOSTER_GIT, RELATIVE_POM_PATH, RELEASED_VERSION_TAG);

        createViewRoleToServiceAccount();
        createConfigMapResourceForApp(TESTSUITE_CONFIGMAP_NAME, "greeting.message: Hello World from a ConfigMap!");

        deploy(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL + getCurrentFMPVersion(), EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);
        waitAfterDeployment(false);

        assertApplication(false);

        openShiftClient.configMaps().inNamespace(testsuiteNamespace).withName(TESTSUITE_CONFIGMAP_NAME).delete();
    }

    @Test
    public void redeploy_springboot_app() throws Exception {


        Repository testRepository = setupSampleTestRepository(SPRING_BOOT_CONFIGMAP_BOOSTER_GIT, RELATIVE_POM_PATH, RELEASED_VERSION_TAG);


        createConfigMapResourceForApp(TESTSUITE_CONFIGMAP_NAME, "greeting.message: Hello World from a ConfigMap!");
        // Make some changes in ConfigMap and rollout
        updateSourceCode(testRepository, RELATIVE_POM_PATH);
        addRedeploymentAnnotations(testRepository, RELATIVE_POM_PATH, ANNOTATION_KEY, ANNOTATION_VALUE, fmpConfigurationFile);
        editConfigMapResourceForApp(TESTSUITE_CONFIGMAP_NAME, "greeting.message: Bonjour World from a ConfigMap!");

        // 2. Re-Deployment
        deploy(testRepository, EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL + getCurrentFMPVersion(), EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE);
        waitAfterDeployment(true);
        assertApplication(true);

        openShiftClient.configMaps().inNamespace(testsuiteNamespace).withName(TESTSUITE_CONFIGMAP_NAME).delete();
    }

    public void deploy(Repository testRepository, String buildGoal, String buildProfile) throws Exception {
        runEmbeddedMavenBuild(testRepository, buildGoal, buildProfile);
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

    private void assertApplication(boolean bIsRedeployed) throws Exception {
        assertThat(openShiftClient).deployment(testsuiteRepositoryArtifactId);
        assertThat(openShiftClient).service(testsuiteRepositoryArtifactId);

        RouteAssert.assertRoute(openShiftClient, testsuiteRepositoryArtifactId);
        // Check if the configmap's properties are accessible by the Application runnning in pod.
        if (bIsRedeployed)
            assertApplicationEndpoint("content", "Bonjour World from a ConfigMap!");
        else
            assertApplicationEndpoint("content", "Hello World from a ConfigMap!");
    }

    private void assertApplicationEndpoint(String key, String value) throws Exception {
        Route applicationRoute = getApplicationRouteWithName(testsuiteRepositoryArtifactId);
        String hostUrl = applicationRoute.getSpec().getHost() + TEST_ENDPOINT;
        Response response = makeHttpRequest(HttpRequestType.GET, "http://" + hostUrl, null);
        String responseContent = new JSONObject(response.body().string()).getString(key);

        if (!responseContent.equals(value))
            throw new AssertionError(String.format("Actual : %s, Expected : %s", responseContent, value));
    }

    private void createConfigMapResourceForApp(String configMapName, String sampleApplicationProperty) {
        Map<String, String> configMapData = new HashMap<>();
        configMapData.put("application.properties", sampleApplicationProperty);

        createConfigMapResource(configMapName, configMapData);
    }

    private void editConfigMapResourceForApp(String configMapName, String messageProperty) {
        Map<String, String> configMapData = new HashMap<>();
        configMapData.put("application.properties", messageProperty);

        createOrReplaceConfigMap(configMapName, configMapData);
    }

    @After
    public void cleanup() throws Exception {
        cleanSampleTestRepository();
    }
}
