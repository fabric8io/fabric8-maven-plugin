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

import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.arquillian.smart.testing.rules.git.GitCloner;
import org.eclipse.jgit.lib.Repository;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.BuiltProject;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.EmbeddedMaven;

import java.io.*;
import java.util.List;
import java.util.Map;

public class Core {

    private final String fabric8PluginGroupId = "io.fabric8";

    private final String fabric8PluginArtifactId = "fabric8-maven-plugin";

    public String testSuiteNamespace;

    public String TESTSUITE_REPOSITORY_ARTIFACT_ID;

    public OpenShiftClient openShiftClient;

    private GitCloner gitCloner;

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

    protected void cleanSampleTestRepository() throws Exception {
        gitCloner.removeClone();
        openShiftClient.close();
    }

    /**
     * Just makes a basic GET request to the url provided as parameter and returns Response.
     *
     * @param hostUrl
     * @return
     * @throws Exception
     */
    public Response makeHttpRequest(String hostUrl) throws Exception {
        OkHttpClient okHttpClient = new OkHttpClient();
        Request request = new Request.Builder().url(hostUrl).get().build();
        return okHttpClient.newCall(request).execute();
    }
}