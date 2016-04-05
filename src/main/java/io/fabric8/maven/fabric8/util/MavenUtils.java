package io.fabric8.maven.fabric8.util;
/*
 * 
 * Copyright 2016 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import io.fabric8.utils.Zips;

/**
 * @author roland
 * @since 31/03/16
 */
public class MavenUtils {

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
}
