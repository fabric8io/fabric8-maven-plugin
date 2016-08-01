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

import java.io.*;
import java.net.URL;
import java.util.*;

import javassist.*;

/**
 * @author roland
 * @since 24/07/16
 */
public class ClassUtil {

    public static Set<String> getResources(String resource) throws IOException {
        Set<String> ret = new HashSet<>();
        for (ClassLoader cl : getClassLoaders()) {
            Enumeration<URL> urlEnum = cl.getResources(resource);
            ret.addAll(extractUrlAsStringsFromEnumeration(urlEnum));
        }
        return ret;
    }

    private static ClassLoader[] getClassLoaders() {
        return new ClassLoader[] {
            Thread.currentThread().getContextClassLoader(),
            PluginServiceFactory.class.getClassLoader()
        };
    }

    private static Set<String> extractUrlAsStringsFromEnumeration(Enumeration<URL> urlEnum) {
        Set<String> ret = new HashSet<String>();
        while (urlEnum.hasMoreElements()) {
            ret.add(urlEnum.nextElement().toExternalForm());
        }
        return ret;
    }

    public static <T> Class<T> classForName(String className) {
        Set<ClassLoader> tried = new HashSet<>();
        for (ClassLoader loader : getClassLoaders()) {
            // Go up the classloader stack to eventually find the server class. Sometimes the WebAppClassLoader
            // hide the server classes loaded by the parent class loader.
            while (loader != null) {
                try {
                    if (!tried.contains(loader)) {
                        return (Class<T>) Class.forName(className, true, loader);
                    }
                } catch (ClassNotFoundException ignored) {}
                tried.add(loader);
                loader = loader.getParent();
            }
        }
        return null;
    }

    /**
     * Find all classes below a certain directory which contain
     * main() classes
     *
     * @param rootDir the directory to start from
     * @return List of classes with "public void static main(String[] args)" methods. Can be empty, but not null.
     * @exception IOException if something goes wrong
     */
    public static List<String> findMainClasses(File rootDir) throws IOException {
        List<String> ret = new ArrayList<>();
        if (!rootDir.exists()) {
            return ret;
        }
        if (!rootDir.isDirectory()) {
            throw new IllegalArgumentException(String.format("Path %s is not a directory",rootDir.getPath()));
        }
        findClasses(ret, rootDir, rootDir.getAbsolutePath() + "/");
        return ret;
    }

    // ========================================================================

    private static final FileFilter DIR_FILTER = new FileFilter() {
        @Override
        public boolean accept(File pathname) {
            return pathname.isDirectory() && !pathname.getName().startsWith(".");
        }
    };

    private static final FileFilter CLASS_FILE_FILTER = new FileFilter() {
		@Override
        public boolean accept(File file) {
            return (file.isFile() && file.getName().endsWith(".class"));
		}
	};


    private static void findClasses(List<String> classes, File dir, String prefix) throws IOException {
        for (File subDir : dir.listFiles(DIR_FILTER)) {
            findClasses(classes, subDir, prefix);
        }

        for (File classFile : dir.listFiles(CLASS_FILE_FILTER)) {
            try (InputStream is = new FileInputStream(classFile)) {
                if (hasMainMethod(is)) {
                    classes.add(convertToClass(classFile.getAbsolutePath(), prefix));
                }
            }
        }
    }

    private static boolean hasMainMethod(InputStream is) throws IOException {
        try {
            ClassPool pool = ClassPool.getDefault();
            CtClass ctClass = pool.makeClass(is);
            CtClass stringClass = pool.get("java.lang.String[]");
            CtMethod mainMethod = ctClass.getDeclaredMethod("main", new CtClass[] { stringClass });
            return mainMethod.getReturnType() == CtClass.voidType &&
                   Modifier.isStatic(mainMethod.getModifiers()) &&
                   Modifier.isPublic(mainMethod.getModifiers());
        } catch (NotFoundException e) {
            return false;
        }
    }

    private static String convertToClass(String name, String prefix) {
        String ret = name.replaceAll("[/\\\\]", ".");
        ret = ret.substring(0, name.length() - ".class".length());
        return ret.substring(prefix.length());
    }
}
