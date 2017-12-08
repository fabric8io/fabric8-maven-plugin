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
import org.eclipse.jetty.http.HttpStatus;
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

    protected final String fabric8PluginGroupId = "io.fabric8";

    protected final String FABRIC8_MAVEN_PLUGIN_KEY = "io.fabric8:fabric8-maven-plugin";

    protected final String fabric8PluginArtifactId = "fabric8-maven-plugin";

    protected String TESTSUITE_NAMESPACE;

    protected String TESTSUITE_REPOSITORY_ARTIFACT_ID;

    protected final String FMP_CONFIGURATION_FILE = "/fmp-plugin-config.xml";

    protected OpenShiftClient openShiftClient;

    private GitCloner gitCloner;

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
        TESTSUITE_NAMESPACE = openShiftClient.getNamespace();
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
        RouteList aRouteList = openShiftClient.routes().inNamespace(TESTSUITE_NAMESPACE).list();
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
        Response response = okHttpClient.newCall(request).execute();
        System.out.println("[" + requestType.getValue() + "] " + hostUrl + " " + HttpStatus.getCode(response.code()));

        return response;
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

        int nOpenShiftProfile = getOpenshiftProfileIndex(model);
        model.getProfiles().get(nOpenShiftProfile).getBuild().getPluginsAsMap().get(FABRIC8_MAVEN_PLUGIN_KEY).setConfiguration(configurationDom);
        writePomModelToFile(pomFile, model);
    }

    protected int getOpenshiftProfileIndex(Model aPomModel) {
        List<Profile> profiles = aPomModel.getProfiles();
        for(int nIndex = 0; nIndex < profiles.size(); nIndex++) {
            if(profiles.get(nIndex).getId().equals("openshift"))
                return nIndex;
        }
        throw new AssertionError("No openshift profile found in project's pom.xml");
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
        DeploymentConfigList deploymentConfigs = openShiftClient.deploymentConfigs().inNamespace(TESTSUITE_NAMESPACE).list();
        for (DeploymentConfig aDeploymentConfig : deploymentConfigs.getItems()) {
            if (aDeploymentConfig.getMetadata() != null && aDeploymentConfig.getMetadata().getAnnotations() != null &&
                    aDeploymentConfig.getMetadata().getAnnotations().containsKey(key))
                return true;
        }
        return false;
    }

    /**
     * It watches over application pod until it becomes ready to serve.
     *
     * @throws Exception
     */
    public void waitTillApplicationPodStarts() throws Exception {
        System.out.println("Waiting to application pod .... ");

        int nPolls = 0;
        // Keep polling till 5 minutes
        while (nPolls < 60) {
            PodList podList = openShiftClient.pods().withLabel("app", TESTSUITE_REPOSITORY_ARTIFACT_ID).list();
            for(Pod pod: podList.getItems()) {
                System.out.println("waitTillApplicationPodStarts() -> Pod : " + pod.getMetadata().getName() + ", isReady : " + KubernetesHelper.isPodReady(pod));
                if(KubernetesHelper.isPodReady(pod)) {
                    System.out.println("OK ✓ ... Pod wait over.");
                    return;
                }
            }
            TimeUnit.SECONDS.sleep(5);
            nPolls++;
        }
        throw new AssertionError("Pod wait timeout! Could not find application pod for " + TESTSUITE_REPOSITORY_ARTIFACT_ID);
    }

    /**
     * This variation is used in order to check for the redeployment scenario, since some
     * annotations are added while making changes in source code, and those are checked so
     * that we are able to differentiate between the redeployed pod and the previously
     * existing pod instance from previous deployment.
     *
     * @param key
     * @param value
     * @throws Exception
     */
    public void waitTillApplicationPodStarts(String key, String value) throws Exception {
        System.out.println("Waiting for application pod .... ");

        int nPolls = 0;
        // Keep polling till 5 minutes
        while (nPolls < 60) {
            PodList podList = openShiftClient.pods().withLabel("app", TESTSUITE_REPOSITORY_ARTIFACT_ID).list();
            for(Pod pod: podList.getItems()) {
                System.out.println("waitTillApplicationPodStarts("+ key + ", " + value +") -> Pod : "
                        + pod.getMetadata().getName() + ", STATUS : " + KubernetesHelper.getPodStatus(pod) + ", isPodReady : " + KubernetesHelper.isPodReady(pod));

                if(pod.getMetadata().getAnnotations().containsKey(key)) {
                    System.out.println(pod.getMetadata().getName() + " is redeployed pod.");
                }
                if(pod.getMetadata().getAnnotations().containsKey(key)
                        && pod.getMetadata().getAnnotations().get(key).equalsIgnoreCase(value)
                        && KubernetesHelper.isPodReady(pod)) {
                    System.out.println("OK ✓ ... Pod wait over.");
                    return;
                }
            }
            nPolls++;
            TimeUnit.SECONDS.sleep(5);
        }
        throw new AssertionError("Pod wait timeout! Could not find application pod for " + TESTSUITE_REPOSITORY_ARTIFACT_ID);
    }

    public void createViewRoleToServiceAccount() throws Exception {
        exec("oc policy add-role-to-user view -z default");
    }

    public void createOrReplaceConfigMap(String name, Map<String, String> data) {
        openShiftClient.configMaps()
                .inNamespace(TESTSUITE_NAMESPACE)
                .withName(name)
                .edit()
                .withData(data)
                .done();
    }

    public void createConfigMapResource(String name, Map<String, String> data) {
        if (openShiftClient.configMaps().inNamespace(TESTSUITE_NAMESPACE).withName(name).get() == null) {
            openShiftClient.configMaps()
                    .inNamespace(TESTSUITE_NAMESPACE)
                    .createNew()
                    .withNewMetadata()
                    .withName(name)
                    .endMetadata()
                    .withData(data)
                    .done();
        }
    }
}
