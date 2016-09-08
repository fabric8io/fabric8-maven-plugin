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
package io.fabric8.maven.plugin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.maven.core.util.VersionUtil;
import io.fabric8.utils.IOHelpers;
import io.fabric8.utils.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.tar.TarUnArchiver;

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

/**
 * Generates a Helm <code>index.yaml</code> file by querying a maven repository
 * to find all the charts available and their releases.
 */
@Mojo(name = "helm-index")
public class HelmIndexMojo extends AbstractArtifactSearchMojo {

    /**
     * The HTML title
     */
    @Parameter(property = "fabric8.helm.indexTitle", defaultValue = "Chart Repository")
    private String helmTitle;

    /**
     * The introduction HTML output before the table
     */
    @Parameter(property = "fabric8.helm.introductionHtmlFile", defaultValue = "${basedir}/src/main/fabric8/site/helm-introduction.html")
    private File introductionHtmlFile;

    /**
     * The HTML for the &lt;head&gt; element
     */
    @Parameter(property = "fabric8.helm.headHtmlFile", defaultValue = "${basedir}/src/main/fabric8/site/helm-head.html")
    private File headHtmlFile;

    /**
     * The HTML for the footer at the end of the &lt;body&gt; element
     */
    @Parameter(property = "fabric8.helm.footerHtmlFile", defaultValue = "${basedir}/src/main/fabric8/site/helm-footer.html")
    private File footerHtmlFile;

    /**
     * The output YAML file
     */
    @Parameter(property = "fabric8.helm.outputYamlFile", defaultValue = "${project.build.directory}/fabric8/site/helm/index.yaml")
    private File outputFile;

    /**
     * The output HTML file
     */
    @Parameter(property = "fabric8.helm.outputHtmlFile", defaultValue = "${project.build.directory}/fabric8/site/helm/index.html")
    private File outputHtmlFile;

    /**
     * Folder where to find project specific files
     */
    @Parameter(property = "fabric8.helm.tempDir", defaultValue = "${project.build.directory}/fabric8/tmp-charts")
    private File tempDir;

    @Component(role = UnArchiver.class, hint = "tar")
    private TarUnArchiver unArchiver;


    @Override
    public void executeInternal() throws MojoExecutionException, MojoFailureException {
        outputFile.getParentFile().mkdirs();

        log.info("Creating Helm Chart Index file at: %s", outputFile);
        List<ArtifactDTO> artifacts = searchMaven("?q=l:%22helm%22");
        Map<String, ChartInfo> charts = new TreeMap<>();
        for (ArtifactDTO artifact : artifacts) {
            addChartInfo(charts, artifact);
        }

        Set<Map.Entry<String, ChartInfo>> entries = charts.entrySet();
        for (Map.Entry<String, ChartInfo> entry : entries) {
            getLog().debug("" + entry.getKey() + " = " + entry.getValue());
        }
        try {
            ObjectMapper mapper = KubernetesHelper.createYamlObjectMapper();
            mapper.writeValue(outputFile, charts);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write results as YAML to: " + outputFile + ". " + e, e);
        }

        generateHTML(outputHtmlFile, charts);
    }


    protected void generateHTML(File outputHtmlFile, Map<String, ChartInfo> charts) throws MojoExecutionException {
        Map<String,SortedSet<ChartInfo>> chartMap = new TreeMap<>();
        for ( ChartInfo chartInfo : charts.values()) {
            String key = chartInfo.getName();
            SortedSet<ChartInfo> set = chartMap.get(key);
            if (set == null) {
                set = new TreeSet(createChartComparator());
                chartMap.put(key, set);
            }
            set.add(chartInfo);
        }
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputHtmlFile))) {
            writer.println("<html>");
            writer.println("<head>");
            writer.println(getHtmlFileContentOrDefault(headHtmlFile,
                    "<link href='style.css' rel=stylesheet>\n" +
                            "<link href='custom.css' rel=stylesheet>\n" +
                            "<title>" + helmTitle + "</title>\n"));
            writer.println("</head>");
            writer.println("<body>");

            writer.println(getHtmlFileContentOrDefault(introductionHtmlFile, "<h1>" + helmTitle + "</h1>"));

            writer.println("<table class='table table-striped table-hover'>");
            writer.println("  <hhead>");
            writer.println("    <tr>");
            writer.println("      <th>Chart</th>");
            writer.println("      <th>Versions</th>");
            writer.println("    </tr>");
            writer.println("  </hhead>");
            writer.println("  <tbody>");
            for (Map.Entry<String, SortedSet<ChartInfo>> entry : chartMap.entrySet()) {
                String key = entry.getKey();
                SortedSet<ChartInfo> set = entry.getValue();
                if (!set.isEmpty()) {
                    ChartInfo first = set.first();
                    HelmMojo.Chart firstChartfile = first.getChartfile();
                    if (firstChartfile == null ) {
                        continue;
                    }
                    String chartDescription = getDescription(firstChartfile);
                    writer.println("    <tr>");
                    writer.println("      <td title='" + chartDescription + "'>");
                    String iconHtml = "";
                    String iconUrl = findIconURL(first, set);
                    if (Strings.isNotBlank(iconUrl)) {
                        iconHtml = "<img class='logo' src='" + iconUrl + "'>";
                    }
                    writer.println("        " + iconHtml + "<span class='chart-name'>" + key + "</span>");
                    writer.println("      </td>");
                    writer.println("      <td class='versions'>");
                    for (ChartInfo chartInfo : set) {
                        HelmMojo.Chart chartfile = chartInfo.getChartfile();
                        if (chartfile == null) {
                            continue;
                        }
                        String description = getDescription(chartfile);
                        String version = chartfile.getVersion();
                        String href = chartInfo.getUrl();
                        writer.println("        <a href='" + href + "' title='" + description + "'>" + version + "</a>");
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

    private String findIconURL(ChartInfo first, Iterable<ChartInfo> chartInfos) throws MojoExecutionException {
        for (ChartInfo chartInfo : chartInfos) {
            HelmMojo.Chart chartfile = chartInfo.getChartfile();
            if (chartfile != null) {
                String icon = chartfile.getIcon();
                if (Strings.isNotBlank(icon)) {
                    return convertRelativeIcon(icon);
                }
            }
        }

        // lets try find the icon from the kubernetes manifest
        String answer = convertRelativeIcon(findManifestIcon(first.getKubernetesManifest()));
        if (Strings.isNullOrBlank(answer)) {
            answer = "https://fabric8.io/images/logos/kubernetes.png";
        }
        return answer;
    }

    private static String getDescription(HelmMojo.Chart firstChartfile) {
        String answer = firstChartfile.getDescription();
        return answer != null ? answer : "";
    }

    private Comparator<ChartInfo> createChartComparator() {
        return new Comparator<ChartInfo>() {
            @Override
            public int compare(ChartInfo c1, ChartInfo c2) {
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

    protected static String getVersion(ChartInfo c1) {
        HelmMojo.Chart chartfile = c1.getChartfile();
        if (chartfile != null) {
            return chartfile.getVersion();
        } else {
            return null;
        }
    }

    protected void addChartInfo(Map<String, ChartInfo> charts, ArtifactDTO artifact) {
        // lets create the latest chart
        ChartInfo latest = new ChartInfo(mavenRepoUrl, artifact);
        String key = artifact.createKey();

        // if we could load the chartfile lets add it
        HelmMojo.Chart chartfile = createChartFile(artifact);
        if (chartfile != null) {
            latest.setChartfile(chartfile);
            charts.put(key, latest);
        } else {
            getLog().warn("Could not find chartfile for " + latest);
        }
    }

    private HelmMojo.Chart createChartFile(ArtifactDTO artifactDTO) {
        File file = resolveArtifactFile(artifactDTO, "helm", "tar.gz");

        File untarDestDir = new File(tempDir, artifactDTO.getG() + "/" + artifactDTO.getA() + "-" + artifactDTO.getV() + "-chart");
        untarDestDir.mkdirs();
        getLog().debug("" + artifactDTO.description() + " extracting " + file.getAbsolutePath() + " to " + untarDestDir);
        unArchiver.setSourceFile(file);
        unArchiver.setDestDirectory(untarDestDir);
        unArchiver.setCompression(TarUnArchiver.UntarCompressionMethod.GZIP);
        unArchiver.extract();

        File tempChartFile = new File(untarDestDir, "Chart.yaml");
        if (!tempChartFile.isFile() || !tempChartFile.exists()) {
            getLog().warn("No Chart.yaml exists at " + tempChartFile);
            return null;
        }
        try {
            return KubernetesHelper.loadYaml(tempChartFile, HelmMojo.Chart.class);
        } catch (IOException e) {
            getLog().warn("Failed to parse " + tempChartFile + ". " + e, e);
            return null;
        }
    }


    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    protected class ChartInfo {
        private String url;
        private String name;
        private HelmMojo.Chart chartfile;

        @JsonIgnore
        private final ArtifactDTO artifact;
        @JsonIgnore
        private Object kubernetesManifest;

        public ChartInfo(String mavenRepoUrl, ArtifactDTO artifact) {
            this.artifact = artifact;
            String artifactId = artifact.getA();
            String version = artifact.getV();
            this.url = mavenRepoUrl + artifact.getG().replace('.', '/') +
                    "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + "-helm.tar.gz";
            this.name = artifactId;
        }

        @Override
        public String toString() {
            return "ChartInfo{" +
                    "url='" + url + '\'' +
                    ", name='" + name + '\'' +
                    ", chartfile=" + chartfile +
                    '}';
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public HelmMojo.Chart getChartfile() {
            return chartfile;
        }

        public void setChartfile(HelmMojo.Chart chartfile) {
            this.chartfile = chartfile;
        }

        public ArtifactDTO getArtifact() {
            return artifact;
        }

        public Object getKubernetesManifest() {
            if (kubernetesManifest == null) {
                kubernetesManifest = loadKubernetesManifestFile(artifact);
            }
            return kubernetesManifest;
        }
    }
}
