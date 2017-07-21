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

package io.fabric8.maven.core.util;

import io.fabric8.utils.Objects;
import io.fabric8.utils.Strings;
import io.fabric8.utils.Zips;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.StringUtils;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.tar.TarArchiver;
import org.codehaus.plexus.archiver.tar.TarLongFileMode;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * @author roland
 * @since 31/03/16
 */
public class MavenUtil {
    private static final transient Logger LOG = LoggerFactory.getLogger(MavenUtil.class);

    private static final String DEFAULT_CONFIG_FILE_NAME = "kubernetes.json";

    public static boolean isKubernetesJsonArtifact(String classifier, String type) {
        return "json".equals(type) && "kubernetes".equals(classifier);
    }

    public static boolean hasKubernetesJson(File f) throws IOException {
        try (FileInputStream fis = new FileInputStream(f); JarInputStream jis = new JarInputStream(fis)) {
            for (JarEntry entry = jis.getNextJarEntry(); entry != null; entry = jis.getNextJarEntry()) {
                if (entry.getName().equals(DEFAULT_CONFIG_FILE_NAME)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static File extractKubernetesJson(File f, Path dir) throws IOException {
        if (hasKubernetesJson(f)) {
            Zips.unzip(new FileInputStream(f), dir.toFile());
            File result = dir.resolve(DEFAULT_CONFIG_FILE_NAME).toFile();
            return result.exists() ? result : null;
        }
        return null;
    }

    public static URLClassLoader getCompileClassLoader(MavenProject project) {
        try {
            List<String> classpathElements = project.getCompileClasspathElements();
            return createClassLoader(classpathElements, project.getBuild().getOutputDirectory());
        } catch (DependencyResolutionRequiredException e) {
            throw new IllegalArgumentException("Cannot resolve artifact from compile classpath",e);
        }
    }

    public static URLClassLoader getTestClassLoader(MavenProject project) {
        try {
            List<String> classpathElements = project.getTestClasspathElements();
            return createClassLoader(classpathElements, project.getBuild().getTestOutputDirectory());
        } catch (DependencyResolutionRequiredException e) {
            throw new IllegalArgumentException("Cannot resolve artifact from test classpath", e);
        }
    }

    public static String createDefaultResourceName(MavenProject project, String ... suffixes) {
        String suffix = StringUtils.join(suffixes, "-");
        String ret = project.getArtifactId() + (suffix.length() > 0 ? "-" + suffix : "");
        if (ret.length() > 63) {
            ret = ret.substring(0,63);
        }
        return ret.toLowerCase();
    }

    // ====================================================

    private static URLClassLoader createClassLoader(List<String> classpathElements, String... paths) {
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
        return createURLClassLoader(urls);
    }

    private static URLClassLoader createURLClassLoader(Collection<URL> jars) {
        return new URLClassLoader(jars.toArray(new URL[jars.size()]));
    }

    private static URL pathToUrl(String path) {
        try {
            File file = new File(path);
            return file.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(String.format("Cannot convert %s to a an URL: %s",path,e.getMessage()),e);
        }
    }


    /**
     * Returns true if the maven project has a dependency with the given groupId
     */
    public static boolean hasDependencyOnAnyArtifactOfGroup(MavenProject project, String groupId) {
        return hasDependency(project, groupId, null);
    }

    /**
     * Returns true if the maven project has a dependency with the given groupId and artifactId (if not null)
     */
    public static boolean hasDependency(MavenProject project, String groupId, String artifactId) {
        return getDependencyVersion(project, groupId, artifactId) != null;
    }

    /**
     * Returns the version associated to the dependency dependency with the given groupId and artifactId (if present)
     */
    public static String getDependencyVersion(MavenProject project, String groupId, String artifactId) {
        Set<Artifact> artifacts = project.getArtifacts();
        if (artifacts != null) {
            for (Artifact artifact : artifacts) {
                String scope = artifact.getScope();
                if (Objects.equal("test", scope)) {
                    continue;
                }
                if (artifactId != null && !Objects.equal(artifactId, artifact.getArtifactId())) {
                    continue;
                }
                if (Objects.equal(groupId, artifact.getGroupId())) {
                    return artifact.getVersion();
                }
            }
        }
        return null;
    }

    public static boolean hasPlugin(MavenProject project, String plugin) {
        return project.getPlugin(plugin) != null;
    }

    /**
     * Returns true if any of the given class names could be found on the given class loader
     */
    public static boolean hasClass(MavenProject project, String ... classNames) {
        URLClassLoader compileClassLoader = getCompileClassLoader(project);
        for (String className : classNames) {
            try {
                compileClassLoader.loadClass(className);
                return true;
            } catch (Throwable e) {
                // ignore
            }
        }
        return false;
    }

    /**
     * Returns true if all the given class names could be found on the given class loader
     */
    public static boolean hasAllClasses(MavenProject project, String ... classNames) {
        URLClassLoader compileClassLoader = getCompileClassLoader(project);
        for (String className : classNames) {
            try {
                compileClassLoader.loadClass(className);
            } catch (Throwable e) {
                // ignore message
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the root maven project or null if there is no maven project
     */
    public static MavenProject getRootProject(MavenProject project) {
        while (project != null) {
            MavenProject parent = project.getParent();
            if (parent == null) {
                break;
            }
            project = parent;
        }
        return project;
    }

    public static void createArchive(File sourceDir, File destinationFile, TarArchiver archiver) throws MojoExecutionException {
        try {
            archiver.setCompression(TarArchiver.TarCompressionMethod.gzip);
            archiver.setLongfile(TarLongFileMode.posix);
            archiver.addDirectory(sourceDir);
            archiver.setDestFile(destinationFile);
            archiver.createArchive();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create archive " + destinationFile + ": " + e, e);
        }
    }


    public static void createArchive(File sourceDir, File destinationFile, ZipArchiver archiver) throws MojoExecutionException {
        try {
            archiver.addDirectory(sourceDir);
            archiver.setDestFile(destinationFile);
            archiver.createArchive();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create archive " + destinationFile + ": " + e, e);
        }
    }

    public static void createArchive(File sourceDir, File destinationFile, JarArchiver archiver) throws MojoExecutionException {
        try {
            archiver.addDirectory(sourceDir);
            archiver.setDestFile(destinationFile);
            archiver.createArchive();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create archive " + destinationFile + ": " + e, e);
        }
    }

    /**
     * Returns the version from the list of pre-configured versions of common groupId/artifact pairs
     */
    public static String getVersion(String groupId, String artifactId) throws IOException {
        String path = "META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties";
        InputStream in = MavenUtil.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IOException("Could not find " + path + " on classath!");
        }
        Properties properties = new Properties();
        try {
            properties.load(in);
        } catch (IOException e) {
            throw new IOException("Failed to load " + path + ". " + e, e);
        }
        String version = properties.getProperty("version");
        if (Strings.isNullOrBlank(version)) {
            throw new IOException("No version property in " + path);

        }
        return version;
    }

}
