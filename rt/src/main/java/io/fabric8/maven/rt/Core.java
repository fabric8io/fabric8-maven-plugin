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
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.dsl.NamespaceVisitFromServerGetWatchDeleteRecreateWaitApplicable;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigList;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteList;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import okhttp3.*;
import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.arquillian.smart.testing.rules.git.GitCloner;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.eclipse.jgit.lib.Repository;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.BuiltProject;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.EmbeddedMaven;
import org.json.JSONObject;
import org.testng.annotations.AfterClass;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Core {

    private final String fabric8PluginGroupId = "io.fabric8";

    public static final String FABRIC8_MAVEN_PLUGIN_KEY = "io.fabric8:fabric8-maven-plugin";

    private final String fabric8PluginArtifactId = "fabric8-maven-plugin";

    public String testSuiteNamespace;

    public String TESTSUITE_REPOSITORY_ARTIFACT_ID;

    public OpenShiftClient openShiftClient;

    private GitCloner gitCloner;

    public Pod applicationPod;

    public CountDownLatch terminateLatch = new CountDownLatch(1);

    public enum HttpRequestType {
        GET("GET"), POST("POST"), PUT("PUT"), DELETE("DELETE");

        private String httpRequestType;

        HttpRequestType(String aHttpRequestType) {
            this.httpRequestType = aHttpRequestType;
        }

        public String getValue() {
            return httpRequestType;
        }
    }

    private Repository cloneRepositoryUsingHttp(String repositoryUrl) throws Exception {
        gitCloner = new GitCloner(repositoryUrl);
        return gitCloner.cloneRepositoryToTempFolder();
    }

    private void modifyPomFileToProjectVersion(Repository aRepository, String relativePomPath) throws Exception {
        /**
         * Read Maven model from the project pom file(Here the pom file is not the test repository is cloned
         * for the test suite. It refers to the rt/ project and fetches the current version of fabric8-maven-plugin
         * (any SNAPSHOT version) and updates that accordingly in the sample cloned project's pom.
         */
        File clonedRepositoryPomFile = new File(aRepository.getWorkTree().getAbsolutePath(), relativePomPath);
        String fmpCurrentVersion = readPomModelFromFile(new File("pom.xml")).getVersion();
        Model model = readPomModelFromFile(clonedRepositoryPomFile);
        TESTSUITE_REPOSITORY_ARTIFACT_ID = model.getArtifactId();

        Map<String, Plugin> aStringToPluginMap = model.getBuild().getPluginsAsMap();
        List<Profile> profiles = model.getProfiles();
        if (aStringToPluginMap.get(fabric8PluginGroupId + ":" + fabric8PluginArtifactId) != null) {
            aStringToPluginMap.get(fabric8PluginGroupId + ":" + fabric8PluginArtifactId).setVersion(fmpCurrentVersion);
        } else {
            for (Profile profile : profiles) {
                if (profile.getBuild() != null && profile.getBuild().getPluginsAsMap().get(fabric8PluginGroupId + ":" + fabric8PluginArtifactId) != null) {
                    profile.getBuild().getPluginsAsMap()
                            .get(fabric8PluginGroupId + ":" + fabric8PluginArtifactId)
                            .setVersion(fmpCurrentVersion);
                }
            }
        }

        // Write back the updated model to the pom file
        writePomModelToFile(clonedRepositoryPomFile, model);
    }

    public static Model readPomModelFromFile(File aFileObj) throws Exception {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        return reader.read(new FileInputStream(aFileObj));
    }

    public void writePomModelToFile(File aFileObj, Model model) throws Exception {
        MavenXpp3Writer writer = new MavenXpp3Writer();
        writer.write(new FileOutputStream(aFileObj), model);
    }

    protected void updateSourceCode(Repository repository, String relativePomPath) throws Exception {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        String baseDir = repository.getWorkTree().getAbsolutePath();
        Model model = reader.read(new FileInputStream(new File(baseDir, relativePomPath)));

        Dependency dependency = new Dependency();
        dependency.setGroupId("org.apache.commons");
        dependency.setArtifactId("commons-lang3");
        dependency.setVersion("3.5");
        model.getDependencies().add(dependency);

        MavenXpp3Writer writer = new MavenXpp3Writer();
        writer.write(new FileOutputStream(new File(baseDir, relativePomPath)), model);
        model.getArtifactId();
    }

    protected Repository setupSampleTestRepository(String repositoryUrl, String relativePomPath) throws Exception {
        openShiftClient = new DefaultOpenShiftClient(new ConfigBuilder().build());
        testSuiteNamespace = openShiftClient.getNamespace();
        Repository repository = cloneRepositoryUsingHttp(repositoryUrl);
        modifyPomFileToProjectVersion(repository, relativePomPath);
        return repository;
    }

    protected void runEmbeddedMavenBuild(Repository sampleRepository, String goals, String profiles) {
        String baseDir = sampleRepository.getWorkTree().getAbsolutePath();
        BuiltProject builtProject = EmbeddedMaven.forProject(baseDir + "/pom.xml")
                .setGoals(goals)
                .setProfiles(profiles)
                .build();

        //assert builtProject.getDefaultBuiltArchive() != null;
    }

    @AfterClass
    protected void cleanSampleTestRepository() throws Exception {
        gitCloner.removeClone();
        openShiftClient.close();
    }

    public Route getApplicationRouteWithName(String name) {
        RouteList aRouteList = openShiftClient.routes().inNamespace(testSuiteNamespace).list();
        for (Route aRoute : aRouteList.getItems()) {
            if (aRoute.getMetadata().getName().equals(name)) {
                return aRoute;
            }
        }
        return null;
    }

    /**
     * Just makes a basic GET request to the url provided as parameter and returns Response.
     *
     * @param hostUrl
     * @return
     * @throws Exception
     */
    public Response makeHttpRequest(HttpRequestType requestType, String hostUrl, String params) throws Exception {
        OkHttpClient okHttpClient = new OkHttpClient();
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        params = (params == null ? new JSONObject().toString() : params);
        Request request = null;
        RequestBody requestBody = RequestBody.create(JSON, params);

        switch (requestType.getValue()) {
            case "GET":
                request = new Request.Builder().url(hostUrl).get().build();
                break;
            case "POST":
                request = new Request.Builder().url(hostUrl).post(requestBody).build();
                break;
            case "PUT":
                request = new Request.Builder().url(hostUrl).put(requestBody).build();
                break;
            case "DELETE":
                request = new Request.Builder().url(hostUrl).delete(requestBody).build();
                break;
        }
        System.out.println("[" + requestType.getValue() + "] " + hostUrl);
        return okHttpClient.newCall(request).execute();
    }

    public int exec(String command) throws Exception {
        Process child = Runtime.getRuntime().exec(command);
        child.waitFor();

        BufferedReader reader = new BufferedReader(new InputStreamReader(child.getInputStream()));
        String line = null;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
        return child.exitValue();
    }

    /**
     * Appends some annotation properties to the fmp's configuration in test repository's pom
     * just to distinguish whether the application is re-deployed or not.
     *
     * @param testRepository
     * @throws Exception
     */
    public void addRedeploymentAnnotations(Repository testRepository, String relativePomPath, String annotationKey, String annotationValue, String fmpConfigFragmentFile) throws Exception {
        File pomFile = new File(testRepository.getWorkTree().getAbsolutePath(), relativePomPath);
        Model model = readPomModelFromFile(pomFile);

        File pomFragment = new File(getClass().getResource(fmpConfigFragmentFile).getFile());
        String pomFragmentStr = String.format(FileUtils.readFileToString(pomFragment), annotationKey, annotationValue, annotationKey, annotationValue);

        Xpp3Dom configurationDom = Xpp3DomBuilder.build(
                new ByteArrayInputStream(pomFragmentStr.getBytes()),
                "UTF-8");

        model.getProfiles().get(0).getBuild().getPluginsAsMap().get(FABRIC8_MAVEN_PLUGIN_KEY).setConfiguration(configurationDom);
        writePomModelToFile(pomFile, model);
    }

    /**
     * A method to check Re-deployment scenario. We append some annotations in all the resources and
     * check that we have those after deployment for 2nd time. This is basically to distinguish
     * deployment's versions.
     *
     * @param key
     * @return
     */
    public boolean checkDeploymentsForAnnotation(String key) {
        DeploymentConfigList deploymentConfigs = openShiftClient.deploymentConfigs().inNamespace(testSuiteNamespace).list();
        for (DeploymentConfig aDeploymentConfig : deploymentConfigs.getItems()) {
            if (aDeploymentConfig.getMetadata() != null && aDeploymentConfig.getMetadata().getAnnotations() != null &&
                    aDeploymentConfig.getMetadata().getAnnotations().containsKey(key))
                return true;
        }
        return false;
    }

    /**
     * Implements a podWatcher just to wait till application pod comes up
     *
     * @throws Exception
     */
    public void waitTillApplicationPodStarts() throws Exception {
        System.out.println("Waiting to application pod .... ");
        TimeUnit.SECONDS.sleep(20);
        /*
        FilterWatchListDeletable<Pod, PodList, Boolean, Watch, Watcher<Pod>> pods = openShiftClient.pods()
                .inNamespace(testSuiteNamespace).withLabel("app", TESTSUITE_REPOSITORY_ARTIFACT_ID);
        Watch podWatcher = pods.watch(new Watcher<Pod>() {
            @Override
            public void eventReceived(Action action, Pod pod) {
                System.out.println("Waiting for pod to start ... " + TESTSUITE_REPOSITORY_ARTIFACT_ID);
                boolean bApplicationPod = pod.getMetadata().getLabels().containsKey("app");
                String podOfApplication = pod.getMetadata().getLabels().get("app");

                System.out.println("Pod Name : " + pod.getMetadata().getName() + ", status : " + KubernetesHelper.getPodStatusText(pod));
                if (action.equals(Action.ADDED) && bApplicationPod) {
                    if (KubernetesHelper.isPodReady(pod) && podOfApplication.equals(TESTSUITE_REPOSITORY_ARTIFACT_ID)) {
                        System.out.println("Found !!!!!");
                        applicationPod = pod;
                        terminateLatch.countDown();
                    }
                }
            }

            @Override
            public void onClose(KubernetesClientException e) {
            }
        });*/

        // Wait till pod starts up
       /* while (terminateLatch.getCount() > 0) {
            try {
                terminateLatch.await(1, TimeUnit.MINUTES);
            } catch (InterruptedException aException) {
                // ignore
            }
            if (applicationPod != null) {
                break;
            }
        }*/
    }

    public void waitTillApplicationPodStarts(String key, String value) throws Exception {
        System.out.println("Waiting to application pod .... ");

        while (true) {
            PodList podList = openShiftClient.pods().withLabel("app", TESTSUITE_REPOSITORY_ARTIFACT_ID).list();
            for(Pod pod: podList.getItems()) {
                if(pod.getMetadata().getAnnotations().containsKey(key)
                        && pod.getMetadata().getAnnotations().get(key).equalsIgnoreCase(value)
                        && KubernetesHelper.isPodReady(pod)) {
                    return;
                }
            }

            TimeUnit.SECONDS.sleep(5);
        }
        */
        System.out.println("OK ✓ ... Pod wait over.");
    }

    public void createViewRoleToServiceAccount() throws Exception {
        exec("oc policy add-role-to-user view -z default");
    }

    public void createOrReplaceConfigMap(String name, Map<String, String> data) {
        openShiftClient.configMaps()
                .inNamespace(testSuiteNamespace)
                .withName(name)
                .edit()
                .withData(data)
                .done();
    }

    public void createConfigMapResource(String name, Map<String, String> data) {
        if (openShiftClient.configMaps().inNamespace(testSuiteNamespace).withName(name).get() == null) {
            openShiftClient.configMaps()
                    .inNamespace(testSuiteNamespace)
                    .createNew()
                    .withNewMetadata()
                    .withName(name)
                    .endMetadata()
                    .withData(data)
                    .done();
        }
    }
}
