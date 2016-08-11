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

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.maven.plugin.helm.Chart;
import io.fabric8.maven.plugin.helm.Maintainer;
import io.fabric8.utils.Files;
import io.fabric8.utils.Strings;
import org.apache.maven.model.Developer;
import org.apache.maven.model.Scm;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.tar.TarArchiver;
import org.codehaus.plexus.archiver.tar.TarLongFileMode;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generates a Helm chart for the kubernetes resources
 */
@Mojo(name = "helm", defaultPhase = LifecyclePhase.PACKAGE)
public class HelmMojo extends AbstractFabric8Mojo {

    public static final String HELM_YAML_EXTENSION = ".yaml";
    public static final String PROPERTY_HELM_CHART_NAME = "fabric8.helm.chart";

    /**
     * The Helm chart name
     */
    @Parameter(property = PROPERTY_HELM_CHART_NAME, defaultValue = "${project.artifactId}")
    private String chartName;

    /**
     * The kubernetes helm distro output dir
     */
    @Parameter(property = "fabric8.helm.outputDir", defaultValue = "${basedir}/target/fabric8/helm")
    private File kubernetesOutputDir;

    /**
     * The openshift helm distro output dir
     */
    @Parameter(property = "fabric8.helm.openshift.outputDir", defaultValue = "${basedir}/target/fabric8/helm-openshift")
    private File openshiftOutputDir;

    /**
     * The kubernetes YAML source directory
     */
    @Parameter(property = "fabric8.helm.sourceDir", defaultValue = "${basedir}/target/classes/META-INF/fabric8/kubernetes")
    private File kubernetesSourceDir;

    /**
     * The OpenShift YAML source directory
     */
    @Parameter(property = "fabric8.helm.sourceDir", defaultValue = "${basedir}/target/classes/META-INF/fabric8/kubernetes")
    private File openshiftSourceDir;


    @Parameter(property = "keywords")
    private List<String> keywords;

    @Parameter(property = "fabric8.helm.engine")
    private String engine;


    @Component
    private MavenProjectHelper projectHelper;

    @Component(role = Archiver.class, hint = "tar")
    private TarArchiver archiver;


    @Override
    public void executeInternal() throws MojoExecutionException, MojoFailureException {
        if (Strings.isNullOrBlank(chartName)) {
            throw new MojoExecutionException("No Chart name defined! Please specify the `" + PROPERTY_HELM_CHART_NAME + "` property");
        }
        generateHelmChartDirectory(this.kubernetesSourceDir, this.kubernetesOutputDir, "helm");
        generateHelmChartDirectory(this.openshiftSourceDir, this.openshiftOutputDir, "helm-openshift");
    }

    protected void generateHelmChartDirectory(File sourceDir, File outputDir, String classifier) throws MojoExecutionException {
        getLog().info("Creating Helm Chart " + chartName + " in " + outputDir + " for manifest folder : " + sourceDir);

        if (Files.isDirectory(outputDir)) {
            Files.recursiveDelete(outputDir);
        }
        if (!sourceDir.isDirectory() || !sourceDir.exists()) {
            getLog().warn("Chart source directory " + sourceDir + " does not exist so cannot make chart " + chartName);
            return;
        }
        if (!containsYamlFiles(sourceDir)) {
            getLog().warn("Chart source directory " + sourceDir + " does not contain any YAML manifests or templates so cannot make chart " + chartName);
            return;
        }
        File templatesDir = new File(outputDir, "templates");
        templatesDir.mkdirs();
        try {
            Files.copy(sourceDir, templatesDir);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to copy manifest files from " + sourceDir + " to chart templates directory: " + templatesDir + ". Reason: " + e, e);
        }

        File outputChartFile = new File(outputDir, "Chart" + HELM_YAML_EXTENSION);
        Chart chart = createChart();
        try {
            KubernetesHelper.saveYaml(chart, outputChartFile);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to save chart " + outputChartFile + ". Reason: " + e, e);
        }

        File basedir = null;
        if (project != null) {
            basedir = project.getBasedir();
            if (basedir != null) {
                String outputReadMeFileName = "README.md";
                try {
                    FilenameFilter filter = new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            return name.toLowerCase(Locale.ENGLISH).startsWith("readme.");
                        }
                    };
                    copyTextFile(basedir, filter, new File(outputDir, outputReadMeFileName));
                } catch (IOException e) {
                    throw new MojoExecutionException("Failed to save " + outputReadMeFileName + ". Reason: " + e, e);
                }
                String outputLicenseFileName = "LICENSE";
                try {
                    FilenameFilter filter = new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            String lower = name.toLowerCase(Locale.ENGLISH);
                            return lower.equals("license") || lower.startsWith("license.");
                        }
                    };
                    copyTextFile(basedir, filter, new File(outputDir, outputLicenseFileName));
                } catch (IOException e) {
                    throw new MojoExecutionException("Failed to save " + outputLicenseFileName + ". Reason: " + e, e);
                }
            }
        }

        getLog().info("Generated Helm Chart " + chartName + " at " + outputDir);

        // now lets create the tarball
        if (basedir != null) {
            createTarGzip(outputDir, new File(basedir, "target/" + chartName + "-" + project.getVersion() + "-" + classifier + ".tar.gz"), classifier);
        }

    }

    protected void createTarGzip(File sourceDir, File outputFile, String classifier) throws MojoExecutionException {
        try {
            archiver.setCompression(TarArchiver.TarCompressionMethod.gzip);
            archiver.setLongfile(TarLongFileMode.posix);
            archiver.addDirectory(sourceDir);
            archiver.setDestFile(outputFile);
            archiver.createArchive();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create helm archive " + outputFile + ". " + e, e);
        }
        projectHelper.attachArtifact(project, "tar.gz", classifier, outputFile);
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

    protected static File copyTextFile(File sourceDir, FilenameFilter filter, File outFile) throws IOException {
        File[] files = sourceDir.listFiles(filter);
        if (files != null && files.length == 1) {
            File sourceFile = files[0];
            Files.copy(sourceFile, outFile);
            return outFile;
        }
        return null;
    }


    protected Chart createChart() {
        Chart answer = new Chart();
        answer.setName(chartName);
        if (project != null) {
            answer.setVersion(project.getVersion());
            answer.setDescription(project.getDescription());
            answer.setHome(project.getUrl());
            answer.setKeywords(keywords);
            answer.setEngine(engine);
            Scm scm = project.getScm();
            if (scm != null) {
                String url = scm.getUrl();
                if (url != null) {
                    List<String> sources = new ArrayList<>();
                    sources.add(url);
                    answer.setSources(sources);
                }
            }
            List<Developer> developers = project.getDevelopers();
            if (developers != null) {
                List<Maintainer> maintainers = new ArrayList<>();
                for (Developer developer : developers) {
                    String email = developer.getEmail();
                    String name = developer.getName();
                    if (Strings.isNotBlank(name) || Strings.isNotBlank(email)) {
                        Maintainer maintainer = new Maintainer(name, email);
                        maintainers.add(maintainer);
                    }
                }
                answer.setMaintainers(maintainers);
            }
        }
        return answer;
    }
}
