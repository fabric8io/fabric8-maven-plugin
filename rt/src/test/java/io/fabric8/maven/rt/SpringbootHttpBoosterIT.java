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

import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigList;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.apache.maven.model.Model;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.File;

import static io.fabric8.kubernetes.assertions.Assertions.assertThat;


/**
 * Created by hshinde on 11/23/17.
 */

public class SpringbootHttpBoosterIT extends Core {

    public static final String SPRING_BOOT_HTTP_BOOSTER_GIT = "https://github.com/snowdrop/spring-boot-http-booster.git";

    public static final String FABRIC8_MAVEN_PLUGIN_KEY = "io.fabric8:fabric8-maven-plugin";

    public static final String ANNOTATION_KEY = "testKey", ANNOTATION_VALUE = "testValue";

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
    }

    private void deployAndAssert(Repository testRepository) {
        runEmbeddedMavenBuild(testRepository, "fabric8:deploy", "openshift");

        assertThat(openShiftClient).deployment("spring-boot-rest-http");
        assertThat(openShiftClient).service("spring-boot-rest-http");

        RouteAssert.assertRoute(openShiftClient, "spring-boot-rest-http");
    }

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
        for(DeploymentConfig aDeploymentConfig : deploymentConfigs.getItems()) {
            if(aDeploymentConfig.getMetadata().getAnnotations().containsKey(ANNOTATION_KEY))
                return true;
        }

        return false;
    }

    @After
    public void cleanup() throws Exception {
        cleanSampleTestRepository();
    }
}
