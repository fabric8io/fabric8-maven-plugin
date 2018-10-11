package io.fabric8.maven.enricher.api.util;

import java.net.URLClassLoader;

public class ProjectClassLoader {

    private URLClassLoader compileClassLoader;
    private URLClassLoader testClassLoader;

    public ProjectClassLoader(URLClassLoader compileClassLoader, URLClassLoader testClassLoader) {
        this.compileClassLoader = compileClassLoader;
        this.testClassLoader = testClassLoader;
    }

    public URLClassLoader getCompileClassLoader() {
        return compileClassLoader;
    }

    public URLClassLoader getTestClassLoader() {
        return testClassLoader;
    }
}
