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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.*;

import io.fabric8.maven.enricher.api.MavenBuildContext;

/**
 * A simple factory for creating services with no-arg constructors from a textual
 * descriptor. This descriptor, which must be a resource loadable by this class'
 * classloader, is a plain text file which looks like
 *
 * <pre>
 *   com.example.MyProjectLabelEnricher
 *   !io.fabric8.maven.fabric8.enhancer.DefaultProjectLabelEnricher
 *   com.example.AnotherEnricher,50
 * </pre>
 *
 * If a line starts with <code>!</code> it is removed if it has been added previously.
 * The optional second numeric value is the order in which the services are returned.
 *
 * @author roland
 * @since 05.11.10
 */
public final class PluginServiceFactory {

    private PluginServiceFactory() {}

    /**
     * Create a list of services ordered according to the ordering given in the
     * service descriptor files. Note, that the descriptor will be looked up
     * in the whole classpath space, which can result in reading in multiple
     * descriptors with a single path. Note, that the reading order for multiple
     * resources with the same name is not defined.
     *
     * @param descriptorPaths a list of resource paths which are handle in the given order.
     *        Normally, default service should be given as first parameter so that custom
     *        descriptors have a chance to remove a default service.
     * @param <T> type of the service objects to create
     * @return a ordered list of created services or an empty list.
     */
    public static <T> List<T> createServiceObjects(MavenBuildContext context, String... descriptorPaths) {
        try {
            ServiceEntry.initDefaultOrder();
            TreeMap<ServiceEntry,T> extractorMap = new TreeMap<ServiceEntry,T>();
            for (String descriptor : descriptorPaths) {
                readServiceDefinitions(context, extractorMap, descriptor);
            }
            ArrayList<T> ret = new ArrayList<T>();
            for (T service : extractorMap.values()) {
                ret.add(service);
            }
            return ret;
        } finally {
            ServiceEntry.removeDefaultOrder();
        }
    }

    private static <T> void readServiceDefinitions(MavenBuildContext context, Map<ServiceEntry, T> extractorMap, String defPath) {
        try {
            for (String url : getResources(defPath)) {
                readServiceDefinitionFromUrl(context, extractorMap, url);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load service from " + defPath + ": " + e, e);
        }
    }

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

    private static <T> void readServiceDefinitionFromUrl(MavenBuildContext context, Map<ServiceEntry, T> extractorMap, String url) {
        String line = null;
        try (LineNumberReader reader = new LineNumberReader(new InputStreamReader(new URL(url).openStream(), "UTF8"))) {
            line = reader.readLine();
            while (line != null) {
                createOrRemoveService(context, extractorMap, line);
                line = reader.readLine();
            }
        } catch (ReflectiveOperationException|IOException e) {
            throw new IllegalStateException("Cannot load service " + line + " defined in " +
                                            url + " : " + e + ". Aborting", e);
        }
    }

    private static <T> void createOrRemoveService(MavenBuildContext context, Map<ServiceEntry, T> extractorMap, String line)
        throws ReflectiveOperationException {
        if (line.length() > 0) {
            ServiceEntry entry = new ServiceEntry(line);
            if (entry.isRemove()) {
                // Removing is a bit complex since we need to find out
                // the proper key since the order is part of equals/hash
                // so we cant fetch/remove it directly
                Set<ServiceEntry> toRemove = new HashSet<ServiceEntry>();
                for (ServiceEntry key : extractorMap.keySet()) {
                    if (key.getClassName().equals(entry.getClassName())) {
                        toRemove.add(key);
                    }
                }
                for (ServiceEntry key : toRemove) {
                    extractorMap.remove(key);
                }
            } else {
                Class<T> clazz = classForName(entry.getClassName());
                if (clazz == null) {
                    throw new ClassNotFoundException("Class " + entry.getClassName() + " could not be found");
                }
                Constructor<T> constructor = clazz.getConstructor(MavenBuildContext.class);
                T ext = constructor.newInstance(context);
                extractorMap.put(entry, ext);
            }
        }
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

    private static void closeReader(LineNumberReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                // Best effort
            }
        }
    }

    // =============================================================================

     static class ServiceEntry implements Comparable<ServiceEntry> {
        private String className;
        private boolean remove;
        private Integer order;

        private static ThreadLocal<Integer> defaultOrderHolder = new ThreadLocal<Integer>() {

            /**
             * Initialise with start value for entries without an explicite order. 100 in this case.
             *
             * @return 100
             */
            @Override
            protected Integer initialValue() {
                return Integer.valueOf(100);
            }
        };

        /**
         * Parse an entry in the service definition. This should be the full qualified classname
         * of a service, optional prefixed with "<code>!</code>" in which case the service is removed
         * from the defaul list. An order value can be appened after the classname with a comma for give a
         * indication for the ordering of services. If not given, 100 is taken for the first entry, counting up.
         *
         * @param line line to parse
         */
        public ServiceEntry(String line) {
            String[] parts = line.split(",");
            if (parts[0].startsWith("!")) {
                remove = true;
                className = parts[0].substring(1);
            } else {
                remove = false;
                className = parts[0];
            }
            if (parts.length > 1) {
                try {
                    order = Integer.parseInt(parts[1]);
                } catch (NumberFormatException exp) {
                    order = nextDefaultOrder();
                }
            } else {
                order = nextDefaultOrder();
            }
        }

        private Integer nextDefaultOrder() {
            Integer defaultOrder = defaultOrderHolder.get();
            defaultOrderHolder.set(defaultOrder + 1);
            return defaultOrder;
        }

        private static void initDefaultOrder() {
            defaultOrderHolder.set(100);
        }

        private static void removeDefaultOrder() {
            defaultOrderHolder.remove();
        }

        private String getClassName() {
            return className;
        }

        private boolean isRemove() {
            return remove;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) { return true; }
            if (o == null || getClass() != o.getClass()) { return false; }

            ServiceEntry that = (ServiceEntry) o;

            return className.equals(that.className);

        }

        @Override
        public int hashCode() {
            return className.hashCode();
        }

        /** {@inheritDoc} */
        public int compareTo(ServiceEntry o) {
            return order - o.order;
        }
    }
}
