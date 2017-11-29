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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.openshift.api.model.*;
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

    private Pod applicationPod;

    private CountDownLatch terminateLatch = new CountDownLatch(1);

    @Test
    public void deploy_springboot_app_once() throws Exception {
        Repository testRepository = setupSampleTestRepository(SPRING_BOOT_HTTP_BOOSTER_GIT);

        deployAndAssert(testRepository);
    }

    @Test
    public void redeploy_springboot_app() throws Exception {
        Repository testRepository = setupSampleTestRepository(SPRING_BOOT_HTTP_BOOSTER_GIT);
        deployAndAssert(testRepository);

        // change the source code
        updateSourceCode(testRepository);
        addRedeploymentAnnotations(testRepository);

        // redeploy and assert
        deployAndAssert(testRepository);
        // check for redeployment specific scenario
        assert checkDeploymentsForAnnotation();
        waitForRunningPodAndCheckEndpoints();
    }

    private void deployAndAssert(Repository testRepository) throws Exception {
        String sampleProjectArtifactId = readPomModelFromFile(new File(testRepository.getWorkTree().getAbsolutePath(), "/pom.xml")).getArtifactId();
        runEmbeddedMavenBuild(testRepository, "fabric8:deploy", "openshift");

        assertThat(openShiftClient).deployment(sampleProjectArtifactId);
        assertThat(openShiftClient).service(sampleProjectArtifactId);

        RouteAssert.assertRoute(openShiftClient, sampleProjectArtifactId);
    }

    /**
     * Appends some annotation properties to the fmp's configuration in test repository's pom
     * just to distinguish whether the application is re-deployed or not.
     *
     * @param testRepository
     * @throws Exception
     */
    private void addRedeploymentAnnotations(Repository testRepository) throws Exception {
        File pomFile = new File(testRepository.getWorkTree().getAbsolutePath(), "/pom.xml");
        Model model = readPomModelFromFile(pomFile);

        Xpp3Dom configurationDom = Xpp3DomBuilder.build(
                new ByteArrayInputStream(
                        ("<configuration>" +
                                "<resources>" +
                                "   <labels> " +
                                "       <all> " +
                                "           <property>" +
                                "               <name>" + ANNOTATION_KEY + "</name> " +
                                "               <value>" + ANNOTATION_VALUE + "</value> " +
                                "           </property> " +
                                "       </all> " +
                                "   </labels> " +
                                "   <annotations> " +
                                "       <all> " +
                                "           <property>" +
                                "               <name>" + ANNOTATION_KEY + "</name> " +
                                "               <value>" + ANNOTATION_VALUE + "</value> " +
                                "           </property> " +
                                "       </all> " +
                                "   </annotations> " +
                                "</resources>" +
                                "</configuration>").getBytes()),
                "UTF-8");

        model.getProfiles().get(0).getBuild().getPluginsAsMap().get(FABRIC8_MAVEN_PLUGIN_KEY).setConfiguration(configurationDom);
        writePomModelToFile(pomFile, model);
    }

    private boolean checkDeploymentsForAnnotation() {
        DeploymentConfigList deploymentConfigs = openShiftClient.deploymentConfigs().inNamespace(testSuiteNamespace).list();
        for (DeploymentConfig aDeploymentConfig : deploymentConfigs.getItems()) {
            if (aDeploymentConfig.getMetadata().getAnnotations().containsKey(ANNOTATION_KEY))
                return true;
        }
        return false;
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
                if (action.equals(Action.ADDED) && bApplicationPod) {
                    applicationPod = pod;
                    terminateLatch.countDown();
                }
            }

            @Override
            public void onClose(KubernetesClientException e) {
            }
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

        assertApplicationPodRoute();
    }

    /**
     * Fetches Route information corresponding to the application pod and checks whether the
     * endpoint is a valid url or not.
     *
     * @throws Exception
     */
    private void assertApplicationPodRoute() throws Exception {
        if (applicationPod == null)
            throw new AssertionError("No application pod found for this application");

        RouteList aRouteList = openShiftClient.routes().inNamespace(testSuiteNamespace).list();
        Route applicationRoute = null;
        String hostRoute;
        String appName = applicationPod.getMetadata().getLabels().get("app");

        for (Route aRoute : aRouteList.getItems()) {
            if (aRoute.getMetadata().getName().equals(appName)) {
                applicationRoute = aRoute;
                break;
            }
        }
        if (applicationRoute != null) {
            hostRoute = applicationRoute.getSpec().getHost();
            if (hostRoute != null) {
                assert checkValidHostRoute("http://" + hostRoute) == true;
            }
        } else {
            throw new AssertionError("[No route found for: " + appName + "]\n");
        }
    }

    @After
    public void cleanup() throws Exception {
        cleanSampleTestRepository();
    }
}
