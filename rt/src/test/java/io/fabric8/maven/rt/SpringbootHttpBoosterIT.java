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
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.openshift.api.model.*;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.apache.maven.model.Model;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.concurrent.CountDownLatch;

import static io.fabric8.kubernetes.assertions.Assertions.assertThat;


/**
 * Created by hshinde on 11/23/17.
 */

public class SpringbootHttpBoosterIT extends Core {

    public static final String SPRING_BOOT_HTTP_BOOSTER_GIT = "https://github.com/snowdrop/spring-boot-http-booster.git";

    public static final String FABRIC8_MAVEN_PLUGIN_KEY = "io.fabric8:fabric8-maven-plugin";

    public static final String ANNOTATION_KEY = "testKey", ANNOTATION_VALUE = "testValue";

    public final String FMP_CONFIGURATION_FILE = "/fmp-plugin-config.xml";

    public final String EMBEDDED_MAVEN_FABRIC8_BUILD_GOAL = "fabric8:deploy", EMBEDDED_MAVEN_FABRIC8_BUILD_PROFILE = "openshift";

    private final String RELATIVE_POM_PATH = "/pom.xml";

    private Pod applicationPod;

    private CountDownLatch terminateLatch = new CountDownLatch(1);

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
        addRedeploymentAnnotations(testRepository, ANNOTATION_KEY, ANNOTATION_VALUE);

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

    /**
     * Checks whether the web application pod is deployed by making an HTTP GET
     * to the corresponding Route.
     *
     * @throws Exception
     */
    private void waitForRunningPodAndCheckEndpoints() throws Exception {
        FilterWatchListDeletable<Pod, PodList, Boolean, Watch, Watcher<Pod>> pods = openShiftClient.pods()
                .inNamespace(testSuiteNamespace);
        Watch podWatcher = pods.watch(new Watcher<Pod>() {
            @Override
            public void eventReceived(Action action, Pod pod) {
                boolean bApplicationPod = pod.getMetadata().getLabels().containsKey("app");
                String podOfApplication = pod.getMetadata().getLabels().get("app");

                if (action.equals(Action.ADDED) && bApplicationPod) {
                    if (KubernetesHelper.isPodReady(pod) && podOfApplication.equals(TESTSUITE_REPOSITORY_ARTIFACT_ID)) {
                        String podStatus = KubernetesHelper.getPodStatusText(pod);
                        applicationPod = pod;
                        terminateLatch.countDown();
                    }
                }
            }

            @Override
            public void onClose(KubernetesClientException e) { }
        });

        // Wait till pod starts up
        while (terminateLatch.getCount() > 0) {
            try {
                terminateLatch.await();
            } catch (InterruptedException aException) {
                // ignore
            }
            if (applicationPod != null) {
                break;
            }
        }
        assertApplicationPodRoute(applicationPod);
    }


    /**
     * Appends some annotation properties to the fmp's configuration in test repository's pom
     * just to distinguish whether the application is re-deployed or not.
     *
     * @param testRepository
     * @throws Exception
     */
    public void addRedeploymentAnnotations(Repository testRepository, String annotationKey, String annotationValue) throws Exception {
        File pomFile = new File(testRepository.getWorkTree().getAbsolutePath(), "/pom.xml");
        Model model = readPomModelFromFile(pomFile);

        File pomFragment = new File(getClass().getResource(FMP_CONFIGURATION_FILE).getFile());
        String pomFragmentStr = String.format(FileUtils.readFileToString(pomFragment), annotationKey, annotationValue, annotationKey, annotationValue);

        Xpp3Dom configurationDom = Xpp3DomBuilder.build(
                new ByteArrayInputStream(pomFragmentStr.getBytes()),
                "UTF-8");

        model.getProfiles().get(0).getBuild().getPluginsAsMap().get(FABRIC8_MAVEN_PLUGIN_KEY).setConfiguration(configurationDom);
        writePomModelToFile(pomFile, model);
    }

    /**
     * Fetches Route information corresponding to the application pod and checks whether the
     * endpoint is a valid url or not.
     *
     * @throws Exception
     */
    public void assertApplicationPodRoute(Pod applicationPod) throws Exception {
        if (applicationPod == null)
            throw new AssertionError("No application pod found for this application");

        RouteList aRouteList = openShiftClient.routes().inNamespace(testSuiteNamespace).list();
        Route applicationRoute = null;
        String hostRoute;

        for (Route aRoute : aRouteList.getItems()) {
            if (aRoute.getMetadata().getName().equals(TESTSUITE_REPOSITORY_ARTIFACT_ID)) {
                applicationRoute = aRoute;
                break;
            }
        }
        if (applicationRoute != null) {
            hostRoute = applicationRoute.getSpec().getHost();
            if (hostRoute != null) {
                assert makeHttpRequest(HttpRequestType.GET,"http://" + hostRoute, null).code() == HttpStatus.SC_OK;;
            }
        } else {
            throw new AssertionError("[No route found for: " + TESTSUITE_REPOSITORY_ARTIFACT_ID + "]\n");
        }
    }

    private boolean checkDeploymentsForAnnotation(String key) {
        DeploymentConfigList deploymentConfigs = openShiftClient.deploymentConfigs().inNamespace(testSuiteNamespace).list();
        for (DeploymentConfig aDeploymentConfig : deploymentConfigs.getItems()) {
            if (aDeploymentConfig.getMetadata().getAnnotations().containsKey(key))
                return true;
        }
        return false;
    }

    @After
    public void cleanup() throws Exception {
        cleanSampleTestRepository();
    }
}
