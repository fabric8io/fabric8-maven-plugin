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

package io.fabric8.maven.plugin.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import io.fabric8.utils.Zips;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.project.MavenProject;

/**
 * @author roland
 * @since 31/03/16
 */
public class MavenUtil {

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

    public static URLClassLoader getCompileClassLoader(MavenProject project) throws IOException {
        try {
            List<String> classpathElements = project.getCompileClasspathElements();
            return createClassLoader(classpathElements, project.getBuild().getOutputDirectory());
        } catch (DependencyResolutionRequiredException e) {
            throw new IOException("Cannot resolve artifact from compile classpath");
        }
    }

    // ====================================================

    private static URLClassLoader createClassLoader(List<String> classpathElements, String... paths) throws MalformedURLException {
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

    private static URL pathToUrl(String path) throws MalformedURLException {
        File file = new File(path);
        return file.toURI().toURL();
    }

}
