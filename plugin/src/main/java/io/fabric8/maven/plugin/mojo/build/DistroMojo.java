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

import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.plugin.mojo.AbstractFabric8Mojo;
import io.fabric8.utils.Files;
import io.fabric8.utils.IOHelpers;
import io.fabric8.utils.Strings;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.zip.ZipArchiver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Generates a tarball of all the dependent kubernetes and openshift templates
 */
@Mojo(name = "distro", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresDependencyResolution = ResolutionScope.COMPILE)
public class DistroMojo extends AbstractFabric8Mojo {

    @Component
    private MavenProjectHelper projectHelper;

    @Component(role = Archiver.class, hint = "zip")
    private ZipArchiver archiver;

    private String[] types = {
            "kubernetes", "openshift"
    };

    private String[] jarPrefixes = {
            "",
            // for WAR files the kubernetes yaml files tend to be prefixed with this:
            "WEB-INF/classes/"
    };

    @Override
    public void executeInternal() throws MojoExecutionException, MojoFailureException {
        File outDir = prepareOutputDir();
        Files.recursiveDelete(outDir);
        Set<Artifact> artifacts = project.getArtifacts();
        for (Artifact artifact : artifacts) {
            String type = artifact.getType();
            if (Artifact.SCOPE_COMPILE.equals(artifact.getScope()) && ("jar".equals(type) || "war".equals(type))) {
                File file = artifact.getFile();
                processDependencyFile(outDir, file);
            }
        }

        File destinationFile = new File(project.getBuild().getDirectory(),
                                        project.getArtifactId() + "-" + project.getVersion() + "-" + "templates" + ".zip");
        MavenUtil.createArchive(outDir, destinationFile, this.archiver);
        projectHelper.attachArtifact(project, "zip", "templates", destinationFile);
    }

    protected void processDependencyFile(File outDir, File jar) throws MojoExecutionException {
        if (jar.isFile() && jar.exists()) {
            String name = jar.getName();
            int idx = name.lastIndexOf(".");
            if (idx > 0) {
                name = name.substring(0, idx);
            }
            name = Strings.stripSuffix(name, "-SNAPSHOT");

            // lets remove the version
            idx = name.lastIndexOf('-');
            if (idx > 0) {
                name = name.substring(0, idx);
            }

            JarFile jarFile = null;
            try {
                jarFile = new JarFile(jar);
            } catch (IOException e) {
                getLog().warn("Could not create JarFile for " + jar + ". " + e, e);
            }

            for (String jarPrefix : jarPrefixes) {
                for (String type : types) {
                    String resourceName = jarPrefix + "META-INF/fabric8/" + type + ".yml";
                    JarEntry jarEntry = jarFile.getJarEntry(resourceName);
                    if (jarEntry != null) {
                        getLog().info("Found entry " + resourceName + " in " + jar);
                        try (InputStream is = jarFile.getInputStream(jarEntry)) {
                            File outFile = new File(outDir, type + "/" + getFolderName(jar) + "/" + name + ".yml");
                            outFile.getParentFile().mkdirs();
                            IOHelpers.copy(is, new FileOutputStream(outFile));
                        } catch (IOException e) {
                            throw new MojoExecutionException("Failed to process " + jar + ". " + e, e);
                        }
                    } else {
                        getLog().debug("No entry " + resourceName + " in " + jar);
                    }
                }
            }
        }
    }

    /**
     * Based on if the jar file is an app or a package lets figure out what folder to put the app inside
     */
    private String getFolderName(File jar) {
        String answer = "microservices";
        File versionDir = jar.getParentFile();
        if (versionDir != null) {
            File artifactFolder = versionDir.getParentFile();
            if (artifactFolder != null) {
                File packageFolder = artifactFolder.getParentFile();
                if (packageFolder != null) {
                    String packageName = packageFolder.getName();
                    if (Strings.isNotBlank(packageName)) {
                        if (Objects.equals("packages", packageName)) {
                            return "main";
                        }
                    }
                }
            }
        }
        return answer;
    }

    private File prepareOutputDir() {
        String dir = getProperty("fabric8.helm.outputDir");
        if (dir == null) {
            dir = project.getBuild().getDirectory() + "/fabric8/distro/";
        }
        File dirF = new File(dir);
        if (Files.isDirectory(dirF)) {
            Files.recursiveDelete(dirF);
        }
        dirF.mkdir();
        return dirF;
    }

    protected URLClassLoader getCompileClassLoader() throws MojoExecutionException {
        try {
            List<String> classpathElements = project.getCompileClasspathElements();
            return createClassLoader(classpathElements, project.getBuild().getOutputDirectory());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to resolve classpath: " + e, e);
        }
    }

    protected URLClassLoader getTestClassLoader() throws MojoExecutionException {
        try {
            List<String> classpathElements = project.getTestClasspathElements();
            return createClassLoader(classpathElements, project.getBuild().getTestOutputDirectory());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to resolve classpath: " + e, e);
        }
    }

    protected URLClassLoader createClassLoader(List<String> classpathElements, String... paths) throws MalformedURLException {
        List<URL> urls = new ArrayList<>();
        for (String path : paths) {
            URL url = pathToUrl(path);
            urls.add(url);
        }
        for (Object object : classpathElements) {
            if (object != null) {
                String path = object.toString();
                URL url = pathToUrl(path);
                urls.add(url);
            }
        }
        getLog().debug("Creating class loader from: " + urls);
        return createURLClassLoader(urls);
    }

    protected static URLClassLoader createURLClassLoader(Collection<URL> jars) {
         return new URLClassLoader(jars.toArray(new URL[jars.size()]));
     }

    private URL pathToUrl(String path) throws MalformedURLException {
        File file = new File(path);
        return file.toURI().toURL();
    }

}
