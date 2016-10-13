/*
 * Copyright 2005-2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.maven.plugin.mojo.internal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.Annotations;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.maven.core.util.VersionUtil;
import io.fabric8.utils.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import static io.fabric8.utils.Strings.isNullOrBlank;

/**
 * Generates a Manifest index file by querying a maven repository
 * to find all the Kubernetes and OpenShift manifests available and their releases.
 */
@Mojo(name = "manifest-index")
public class ManifestIndexMojo extends AbstractArtifactSearchMojo {

    /**
     * The HTML title
     */
    @Parameter(property = "fabric8.manifest.indexTitle", defaultValue = "Manifest Repository")
    private String manifestTitle;
    /**
     * The Kubernetes introduction HTML output before the table
     */
    @Parameter(property = "fabric8.manifest.kubernetes.introductionHtmlFile", defaultValue = "${basedir}/src/main/fabric8/site/kubernetes-introduction.html")
    private File kubernetesIntroductionHtmlFile;
    /**
     * The OpenShift introduction HTML output before the table
     */
    @Parameter(property = "fabric8.manifest.openshift.introductionHtmlFile", defaultValue = "${basedir}/src/main/fabric8/site/openshift-introduction.html")
    private File openshiftIntroductionHtmlFile;
    /**
     * The Kubernetes HTML for the &lt;head&gt; element
     */
    @Parameter(property = "fabric8.manifest.kubernetes.headHtmlFile", defaultValue = "${basedir}/src/main/fabric8/site/kubernetes-head.html")
    private File kubernetesHeadHtmlFile;
    /**
     * The OpenShift HTML for the &lt;head&gt; element
     */
    @Parameter(property = "fabric8.manifest.openshift.headHtmlFile", defaultValue = "${basedir}/src/main/fabric8/site/openshift-head.html")
    private File openshiftHeadHtmlFile;
    /**
     * The Kubernetes HTML for the footer at the end of the &lt;body&gt; element
     */
    @Parameter(property = "fabric8.manifest.kubernetes.headHtmlFile", defaultValue = "${basedir}/src/main/fabric8/site/kubernetes-footer.html")
    private File kubernetesFooterHtmlFile;
    /**
     * The OpenShift HTML for the footer at the end of the &lt;body&gt; element
     */
    @Parameter(property = "fabric8.manifest.openshift.headHtmlFile", defaultValue = "${basedir}/src/main/fabric8/site/openshift-footer.html")
    private File openshiftFooterHtmlFile;
    /**
     * The output YAML file
     */
    @Parameter(property = "fabric8.manifest.outputYamlFile", defaultValue = "${project.build.directory}/fabric8/site/manifests/index.yaml")
    private File outputFile;
    /**
     * The output HTML file
     */
    @Parameter(property = "fabric8.manifest.outputHtmlDir", defaultValue = "${project.build.directory}/fabric8/site/manifests")
    private File outputHtmlDir;
    /**
     * Folder where to find project specific files
     */
    @Parameter(property = "fabric8.manifest.tempDir", defaultValue = "${project.build.directory}/fabric8/tmp-manifests")
    private File tempDir;
    @Parameter(property = "fabric8.manifest.index.maxVersionsPerApp", defaultValue = "8")
    private int maxVersionsPerApp;

    private static String getDescription(ManifestInfo manifestInfo) {
        String answer = manifestInfo.getDescription();
        return answer != null ? answer : "";
    }

    protected static String getVersion(ManifestInfo manifestInfo) {
        return manifestInfo.getVersion();
    }

    @Override
    public void executeInternal() throws MojoExecutionException, MojoFailureException {
        outputFile.getParentFile().mkdirs();

        log.info("Creating Manifest Index file at: %s", outputFile);
        List<ArtifactDTO> artifacts = searchMaven("?q=l:%22kubernetes%22");
        Map<String, ManifestInfo> manifests = new TreeMap<>();
        for (ArtifactDTO artifact : artifacts) {
            addManifestInfo(manifests, artifact);
        }

        Set<Map.Entry<String, ManifestInfo>> entries = manifests.entrySet();
        for (Map.Entry<String, ManifestInfo> entry : entries) {
            getLog().debug("" + entry.getKey() + " = " + entry.getValue());
        }
        try {
            ObjectMapper mapper = KubernetesHelper.createYamlObjectMapper();
            mapper.writeValue(outputFile, manifests);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write results as YAML to: " + outputFile + ". " + e, e);
        }

        generateHTML(new File(outputHtmlDir, "kubernetes.html"), manifests, true, kubernetesIntroductionHtmlFile, kubernetesHeadHtmlFile, kubernetesFooterHtmlFile);
        generateHTML(new File(outputHtmlDir, "openshift.html"), manifests, false, openshiftIntroductionHtmlFile, openshiftHeadHtmlFile, openshiftFooterHtmlFile);
    }

    protected void generateHTML(File outputHtmlFile, Map<String, ManifestInfo> manifests, boolean kubernetes, File introductionHtmlFile, File headHtmlFile, File footerHtmlFile) throws MojoExecutionException {
        Map<String, SortedSet<ManifestInfo>> manifestMap = new TreeMap<>();
        for (ManifestInfo manifestInfo : manifests.values()) {
            String key = manifestInfo.getName();
            SortedSet<ManifestInfo> set = manifestMap.get(key);
            if (set == null) {
                set = new TreeSet(createManifestComparator());
                manifestMap.put(key, set);
            }
            set.add(manifestInfo);
        }
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputHtmlFile))) {
            writer.println("<html>");
            writer.println("<head>");
            writer.println(getHtmlFileContentOrDefault(headHtmlFile,
                    "<link href='style.css' rel=stylesheet>\n" +
                            "<link href='custom.css' rel=stylesheet>\n" +
                            "<title>" + manifestTitle + "</title>\n"));
            writer.println("</head>");
            writer.println("<body>");

            writer.println(getHtmlFileContentOrDefault(introductionHtmlFile, "<h1>" + manifestTitle + "</h1>"));

            writer.println("<table class='table table-striped table-hover'>");
            writer.println("  <hhead>");
            writer.println("    <tr>");
            writer.println("      <th>Manifest</th>");
            writer.println("      <th>Versions</th>");
            writer.println("    </tr>");
            writer.println("  </hhead>");
            writer.println("  <tbody>");
            for (Map.Entry<String, SortedSet<ManifestInfo>> entry : manifestMap.entrySet()) {
                String key = entry.getKey();
                SortedSet<ManifestInfo> set = entry.getValue();
                if (!set.isEmpty()) {
                    ManifestInfo first = set.first();
                    first.configure(this);
                    if (!first.isValid()) {
                        continue;
                    }

                    String manifestDescription = getDescription(first);
                    writer.println("    <tr>");
                    writer.println("      <td title='" + manifestDescription + "'>");
                    String iconHtml = "";
                    String iconUrl = findIconURL(set);
                    if (Strings.isNotBlank(iconUrl)) {
                        iconHtml = "<img class='logo' src='" + iconUrl + "'>";
                    }
                    writer.println("        " + iconHtml + "<span class='manifest-name'>" + key + "</span>");
                    writer.println("      </td>");
                    writer.println("      <td class='versions'>");
                    int count = 0;
                    for (ManifestInfo manifestInfo : set) {
                        if (maxVersionsPerApp > 0 && ++count > maxVersionsPerApp) {
                            break;
                        }
                        String description = getDescription(manifestInfo);
                        String version = manifestInfo.getVersion();
                        String href = kubernetes ? manifestInfo.getKubernetesUrl() : manifestInfo.getOpenShiftUrl();
                        String versionId = manifestInfo.getId();
                        String command = kubernetes ? "kubectl" : "oc";
                        writer.println("        <a class='btn btn-default' role='button' data-toggle='collapse' href='#" + versionId + "' aria-expanded='false' aria-controls='" + versionId + "' title='" + description + "'>\n" +
                                version + "\n" +
                                "</a>\n" +
                                "<div class='collapse' id='" + versionId + "'>\n" +
                                "  <div class='well'>\n" +
                                "    <p>To install version <b>" + version + "</b> of <b>" + key + "</b> type the following command:</p>\n" +
                                "    <code>" + command + " apply -f " + href + "</code>\n" +
                                "    <div class='version-buttons'><a class='btn btn-primary' title='Download the YAML manifest for " + key + " version " + version + "' href='" + href + "'><i class='fa fa-download' aria-hidden='true'></i> Download Manifest</a> " +
                                "<a class='btn btn-primary' target='gofabric8' title='Run this application via the go.fabric8.io website' href='https://go.fabric8.io/?manifest=" + href + "'><i class='fa fa-external-link' aria-hidden='true'></i> Run via browser</a></div>\n" +
                                "  </div>\n" +
                                "</div>");
                    }
                    writer.println("      </td>");
                    writer.println("    </tr>");
                }
            }
            writer.println("  </tbody>");
            writer.println("  </table>");
            writer.println(getHtmlFileContentOrDefault(footerHtmlFile, ""));
            writer.println("</body>");
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write to " + outputHtmlFile + ". " + e, e);
        }
    }

    private String findIconURL(Iterable<ManifestInfo> manifestInfos) {
        for (ManifestInfo manifestInfo : manifestInfos) {
            String icon = manifestInfo.getIcon();
            if (Strings.isNotBlank(icon)) {
                return icon;
            }
        }
        return "https://fabric8.io/images/logos/kubernetes.png";
    }

    private Comparator<ManifestInfo> createManifestComparator() {
        return new Comparator<ManifestInfo>() {
            @Override
            public int compare(ManifestInfo c1, ManifestInfo c2) {
                String v1 = getVersion(c1);
                String v2 = getVersion(c2);
                int answer = VersionUtil.compareVersions(v1, v2);
                // lets sort in reverse order
                if (answer > 0) {
                    return -1;
                } else if (answer < 0) {
                    return 1;
                }
                return 0;
            }
        };
    }

    protected void addManifestInfo(Map<String, ManifestInfo> manifests, ArtifactDTO artifact) {
        List<String> ec = artifact.getEc();
        if (ec != null && ec.indexOf("-openshift.yml") >= 0 && ec.indexOf("-openshift.yml") >= 0) {
            // lets create the latest manifest
            ManifestInfo latest = new ManifestInfo(mavenRepoUrl, artifact);
            String key = artifact.createKey();
            manifests.put(key, latest);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    protected class ManifestInfo {
        @JsonIgnore
        private final ArtifactDTO artifact;
        private String kubernetesUrl;
        private String openShiftUrl;
        private String icon;
        private String name;
        private String version;
        private String description;
        private String buildUrl;
        private String gitUrl;
        private String gitCommit;
        private String docsUrl;
        @JsonIgnore
        private Object kubernetesManifest;
        @JsonIgnore
        private Object openShiftManifest;

        public ManifestInfo(String mavenRepoUrl, ArtifactDTO artifact) {
            this.artifact = artifact;
            this.version = artifact.getV();
            String artifactId = artifact.getA();
            String urlPrefix = mavenRepoUrl + artifact.getG().replace('.', '/') +
                    "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
            this.kubernetesUrl = urlPrefix + "-kubernetes.yml";
            this.openShiftUrl = urlPrefix + "-openshift.yml";
            this.name = artifactId;
        }

        private void updateFromManifest(Object manifest) {
            if (isNullOrBlank(description)) {
                description = findManifestAnnotation(manifest, "description");
            }
            if (isNullOrBlank(icon)) {
                icon = convertRelativeIcon(findManifestIcon(manifest));
            }
            if (isNullOrBlank(buildUrl)) {
                buildUrl = findManifestAnnotation(manifest, Annotations.Builds.BUILD_URL);
            }
            if (isNullOrBlank(gitUrl)) {
                gitUrl = findManifestAnnotation(manifest, Annotations.Builds.GIT_URL);
            }
            if (isNullOrBlank(gitCommit)) {
                gitCommit = findManifestAnnotation(manifest, Annotations.Builds.GIT_COMMIT);
            }
            if (isNullOrBlank(docsUrl)) {
                docsUrl = findManifestAnnotation(manifest, Annotations.Builds.DOCS_URL);
            }
        }

        @Override
        public String toString() {
            return "ManifestInfo{" +
                    "name='" + name + '\'' +
                    ", version='" + version + '\'' +
                    '}';
        }

        public void configure(AbstractArtifactSearchMojo mojo) {
            // if we could load the manifestfile lets add it
            if (kubernetesManifest == null) {
                Object manifest = mojo.loadKubernetesManifestFile(artifact);
                setKubernetesManifest(manifest);
                if (manifest == null) {
                    mojo.getLog().warn("Could not find kubernetes manifest for " + this);
                }
            }
            if (openShiftManifest == null) {
                Object manifest = mojo.loadOpenShiftManifestFile(artifact);
                setOpenShiftManifest(manifest);
                if (manifest == null) {
                    mojo.getLog().warn("Could not find openshift manifest for " + this);
                }
            }

        }

        public String getKubernetesUrl() {
            return kubernetesUrl;
        }

        public void setKubernetesUrl(String kubernetesUrl) {
            this.kubernetesUrl = kubernetesUrl;
        }

        public String getOpenShiftUrl() {
            return openShiftUrl;
        }

        public void setOpenShiftUrl(String openShiftUrl) {
            this.openShiftUrl = openShiftUrl;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public Object getKubernetesManifest() {
            return kubernetesManifest;
        }

        public void setKubernetesManifest(Object kubernetesManifest) {
            this.kubernetesManifest = kubernetesManifest;
            updateFromManifest(kubernetesManifest);
        }

        public Object getOpenShiftManifest() {
            return openShiftManifest;
        }

        public void setOpenShiftManifest(Object openShiftManifest) {
            this.openShiftManifest = openShiftManifest;
            updateFromManifest(kubernetesManifest);
        }

        public String getBuildUrl() {
            return buildUrl;
        }

        public void setBuildUrl(String buildUrl) {
            this.buildUrl = buildUrl;
        }

        public String getGitUrl() {
            return gitUrl;
        }

        public void setGitUrl(String gitUrl) {
            this.gitUrl = gitUrl;
        }

        public String getGitCommit() {
            return gitCommit;
        }

        public void setGitCommit(String gitCommit) {
            this.gitCommit = gitCommit;
        }

        public String getDocsUrl() {
            return docsUrl;
        }

        public void setDocsUrl(String docsUrl) {
            this.docsUrl = docsUrl;
        }

        public boolean isValid() {
            return kubernetesManifest != null && openShiftManifest != null && kubernetesUrl != null && openShiftUrl != null;
        }

        /**
         * Returns an ID we can use as a target in HTML / bootstrap / kquery
         * @return
         */
        public String getId() {
            String answer = name + "_" + version;
            answer = answer.replace('.', '_').replace('-', '_');
            return answer;
        }
    }

}
