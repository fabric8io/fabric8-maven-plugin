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
package io.fabric8.maven.plugin.mojo.build;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.kubernetes.api.Annotations;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.maven.core.config.HelmConfig;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.plugin.mojo.AbstractFabric8Mojo;
import io.fabric8.utils.Files;
import io.fabric8.utils.IOHelpers;
import io.fabric8.utils.Strings;
import org.apache.maven.model.Developer;
import org.apache.maven.model.Scm;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.tar.TarArchiver;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Generates a Helm chart for the kubernetes resources
 */
@Mojo(name = "helm", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST)
public class HelmMojo extends AbstractFabric8Mojo {

    @Parameter
    private HelmConfig helm;

    /**
     * The generated kubernetes YAML file
     */
    @Parameter(property = "fabric8.kubernetesManifest", defaultValue = "${basedir}/target/classes/META-INF/fabric8/kubernetes.yml")
    private File kubernetesManifest;

    @Component
    private MavenProjectHelper projectHelper;

    @Component(role = Archiver.class, hint = "tar")
    private TarArchiver archiver;

    @Override
    public void executeInternal() throws MojoExecutionException, MojoFailureException {
        String chartName = getChartName();

        for (HelmConfig.HelmType type : getHelmTypes()) {
            generateHelmChartDirectory(chartName, type);
        }
    }

    protected void generateHelmChartDirectory(String chartName, HelmConfig.HelmType type) throws MojoExecutionException {
        File outputDir = prepareOutputDir(type);
        File sourceDir = checkSourceDir(chartName, type);
        if (sourceDir == null) {
            return;
        }
        log.info("Creating Helm Chart \"%s\" for %s", chartName, type.getDescription());
        log.verbose("SourceDir: %s", sourceDir);
        log.verbose("OutputDir: %s", outputDir);

        // Copy over all resource descriptors into the helm templates dir
        copyResourceFilesToTemplatesDir(outputDir, sourceDir);

        // Save Helm chart
        createChartYaml(chartName, outputDir);

        // Copy over support files
        copyTextFile(outputDir, "README");
        copyTextFile(outputDir, "LICENSE");

        // now lets create the tarball
        File destinationFile = new File(project.getBuild().getDirectory(),
                                        chartName + "-" + project.getVersion() + "-" + type.getClassifier() + ".tar.gz");
        MavenUtil.createArchive(outputDir.getParentFile(), destinationFile, this.archiver);
        projectHelper.attachArtifact(project, "tar.gz", type.getClassifier(), destinationFile);
    }

    private String getChartName() {
        String ret = getProperty("fabric8.helm.chart");
        if (ret != null) {
            return ret;
        }
        if (helm != null) {
            ret = helm.getChart();
        }
        return ret != null ? ret : project.getArtifactId();
    }

    private File prepareOutputDir(HelmConfig.HelmType type) {
        String dir = getProperty("fabric8.helm.outputDir");
        if (dir == null) {
            dir = String.format("%s/fabric8/helm/%s/%s",
                                project.getBuild().getDirectory(),
                                type.getSourceDir(),
                                getChartName());
        }
        File dirF = new File(dir);
        if (Files.isDirectory(dirF)) {
            Files.recursiveDelete(dirF);
        }
        return dirF;
    }

    private File checkSourceDir(String chartName, HelmConfig.HelmType type) {
        String dir = getProperty("fabric8.helm.sourceDir");
        if (dir == null) {
            dir = project.getBuild().getOutputDirectory() + "/META-INF/fabric8/" + type.getSourceDir();
        }
        File dirF = new File(dir);
        if (!dirF.isDirectory() || !dirF.exists()) {
            log.warn("Chart source directory %s does not exist so cannot make chart %s. " +
                     "Probably you need run 'mvn fabric8:resource' before.", dirF, chartName);
            return null;
        }
        if (!containsYamlFiles(dirF)) {
            log.warn("Chart source directory %s does not contain any YAML manifest to make chart %s. " +
                     "Probably you need run 'mvn fabric8:resource' before.", dirF, chartName);
            return null;
        }
        return dirF;
    }

    private List<HelmConfig.HelmType> getHelmTypes() {
        String helmTypeProp = getProperty("fabric8.helm.type");
        if (!Strings.isNullOrBlank(helmTypeProp)) {
            List<String> propTypes = Strings.splitAsList(helmTypeProp, ",");
            List<HelmConfig.HelmType> ret = new ArrayList<>();
            for (String prop : propTypes) {
                ret.add(HelmConfig.HelmType.valueOf(prop.trim().toLowerCase()));
            }
            return ret;
        }
        if (helm != null) {
            List<HelmConfig.HelmType> types = helm.getType();
            if (types != null && types.size() > 0) {
                return types;
            }
        }
        return Arrays.asList(HelmConfig.HelmType.kubernetes);
    }

    private void createChartYaml(String chartName, File outputDir) throws MojoExecutionException {
        Chart chart = helm != null ?
            new Chart(chartName, project, helm.getKeywords(), helm.getEngine()) :
            new Chart(chartName, project);

        String iconUrl = findIconURL();
        getLog().debug("Found icon: " + iconUrl);
        if (Strings.isNotBlank(iconUrl)) {
            chart.setIcon(iconUrl);
        }
        File outputChartFile = new File(outputDir, "Chart.yaml");
        try {
            KubernetesHelper.saveYaml(chart, outputChartFile);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to save chart " + outputChartFile + ": " + e, e);
        }
    }

    private String findIconURL() throws MojoExecutionException {
        String answer = null;
        if (kubernetesManifest != null && kubernetesManifest.isFile()) {
            Object dto = null;
            try {
                dto = KubernetesHelper.loadYaml(kubernetesManifest, KubernetesResource.class);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to load kubernetes YAML " + kubernetesManifest + ". " + e, e);
            }
            if (dto instanceof HasMetadata) {
                answer = KubernetesHelper.getOrCreateAnnotations((HasMetadata) dto).get(Annotations.Builds.ICON_URL);
            }
            if (Strings.isNullOrBlank(answer) && dto instanceof KubernetesList) {
                KubernetesList list = (KubernetesList) dto;
                List<HasMetadata> items = list.getItems();
                if (items != null) {
                    for (HasMetadata item : items) {
                        answer = KubernetesHelper.getOrCreateAnnotations(item).get(Annotations.Builds.ICON_URL);
                        if (Strings.isNotBlank(answer)) {
                            break;
                        }
                    }
                }
            }
        } else {
            getLog().warn("No kubernetes manifest file has been generated yet by the fabric8:resource goal at: " + kubernetesManifest);
        }
        return answer;
    }

    private void copyResourceFilesToTemplatesDir(File outputDir, File sourceDir) throws MojoExecutionException {
        File templatesDir = new File(outputDir, "templates");
        templatesDir.mkdirs();
        File[] files = sourceDir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.endsWith(".yml")) {
                    name = Strings.stripSuffix(name, ".yml") + ".yaml";
                }
                File targetFile = new File(templatesDir, name);
                try {
                    // lets escape any {{ or }} characters to avoid creating invalid templates
                    String text = IOHelpers.readFully(file);
                    text = escapeYamlTemplate(text);
                    IOHelpers.writeFully(targetFile, text);
                } catch (IOException e) {
                    throw new MojoExecutionException("Failed to copy manifest files from " + file +
                            " to " + targetFile + ": " + e, e);
                }
            }
        }
    }

    public static String escapeYamlTemplate(String template) {
        StringBuffer answer = new StringBuffer();
        int count = 0;
        char last = 0;
        for (int i = 0, size = template.length(); i < size; i++) {
            char ch = template.charAt(i);
            if (ch == '{' || ch == '}') {
                if (count == 0) {
                    last = ch;
                    count = 1;
                } else {
                    if (ch == last) {
                        answer.append( ch == '{' ? "{{\"{{\"}}" : "{{\"}}\"}}");
                    } else {
                        answer.append(last);
                        answer.append(ch);
                    }
                    count = 0;
                    last = 0;
                }
            } else {
                if (count > 0) {
                    answer.append(last);
                }
                answer.append(ch);
                count = 0;
                last = 0;
            }
        }
        if (count > 0) {
            answer.append(last);
        }
        return answer.toString();
    }

    private boolean containsYamlFiles(File sourceDir) {
        File[] files = sourceDir.listFiles();
        if (files != null) {
            for (File file : files) {
                String lower = file.getName().toLowerCase();
                if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
                    return true;
                }
            }
        }
        return false;
    }

    private void copyTextFile(File outputDir, final String srcFile) throws MojoExecutionException {
        try {
            FilenameFilter filter = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    String lower = name.toLowerCase(Locale.ENGLISH);
                    return lower.equals(srcFile.toLowerCase()) || lower.startsWith(srcFile.toLowerCase() + ".");
                }
            };
            copyFirstFile(project.getBasedir(), filter, new File(outputDir, srcFile));
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to save " + srcFile + ": " + e, e);
        }
    }

    protected void copyFirstFile(File sourceDir, FilenameFilter filter, File outFile) throws IOException {
        File[] files = sourceDir.listFiles(filter);
        if (files != null && files.length > 0) {
            File sourceFile = files[0];
            Files.copy(sourceFile, outFile);
        }
        if (files.length > 1) {
            log.warn("Found %d of %s files. Using first one %s", files.length, outFile, files[0]);
        }
    }

    // =================================================================================================================
    /**
     * Represents the <a href="https://github.com/kubernetes/helm">Helm</a>
     * <a href="https://github.com/kubernetes/helm/blob/master/pkg/proto/hapi/chart/metadata.pb.go#L50">Chart.yaml file</a>
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class Chart {
        @JsonProperty
        private String name;
        @JsonProperty
        private String home;
        @JsonProperty
        private List<String> sources;
        @JsonProperty
        private String version;
        @JsonProperty
        private String description;
        @JsonProperty
        private List<String> keywords;
        @JsonProperty
        private List<Maintainer> maintainers;
        @JsonProperty
        private String engine;
        @JsonProperty
        private String icon;

        public Chart() {
        }

        public Chart(String name, String version) {
            this.name = name;
            this.version = version;
        }

        public Chart(String name, MavenProject project) {
            this(name, project, null, null);
        }

        public Chart(String name, MavenProject project, List<String> keywords, String engine) {
            this.name = name;
            this.keywords = keywords;
            this.engine = engine;

            this.name = name;
            if (project != null) {
                this.version = project.getVersion();
                this.description = project.getDescription();
                this.home = project.getUrl();
                this.keywords = keywords;
                this.engine = engine;

                Scm scm = project.getScm();
                if (scm != null) {
                    String url = scm.getUrl();
                    if (url != null) {
                        List<String> sources1 = new ArrayList<>();
                        sources1.add(url);
                        this.sources = sources1;
                    }
                }
                List<Developer> developers = project.getDevelopers();
                if (developers != null) {
                    List<Maintainer> maintainers1 = new ArrayList<>();
                    for (Developer developer : developers) {
                        String email = developer.getEmail();
                        String devName = developer.getName();
                        if (Strings.isNotBlank(devName) || Strings.isNotBlank(email)) {
                            Maintainer maintainer = new Maintainer(devName, email);
                            maintainers1.add(maintainer);
                        }
                    }
                    this.maintainers = maintainers1;
                }
            }
        }

        @Override
        public String toString() {
            return "Chart{" +
                    "name='" + name + '\'' +
                    ", home='" + home + '\'' +
                    ", version='" + version + '\'' +
                    '}';
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getHome() {
            return home;
        }

        public void setHome(String home) {
            this.home = home;
        }

        public List<String> getSources() {
            return sources;
        }

        public void setSources(List<String> sources) {
            this.sources = sources;
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

        public List<String> getKeywords() {
            return keywords;
        }

        public void setKeywords(List<String> keywords) {
            this.keywords = keywords;
        }

        public List<Maintainer> getMaintainers() {
            return maintainers;
        }

        public void setMaintainers(List<Maintainer> maintainers) {
            this.maintainers = maintainers;
        }

        public String getEngine() {
            return engine;
        }

        public void setEngine(String engine) {
            this.engine = engine;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        /**
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public static class Maintainer {

            @JsonProperty
            private String name;

            @JsonProperty
            private String email;

            public Maintainer() {}

            public Maintainer(String name, String email) {
                this.name = name;
                this.email = email;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getEmail() {
                return email;
            }

            public void setEmail(String email) {
                this.email = email;
            }
        }
    }
}
