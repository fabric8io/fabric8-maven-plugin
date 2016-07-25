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

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

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
}
