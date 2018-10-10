package io.fabric8.maven.enricher.api.util;

import java.net.URLClassLoader;

public class ClassLoaderWrapper {

    private URLClassLoader classLoader;

    public ClassLoaderWrapper(URLClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public URLClassLoader getClassLoader() {
        return classLoader;
    }
}
