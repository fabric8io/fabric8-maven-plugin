package io.fabric8.maven.generator.webapp;

import org.apache.maven.project.MavenProject;

/**
 * @author kameshs
 */
public class AppServerDetectorFactory {

    public static final AppServerDetectorFactory INSTANCE = new AppServerDetectorFactory();

    private AppServerDetectorFactory() {

    }

    public AppServerDetector getAppServerDetector(final Kind kind, MavenProject mavenProject) {

        switch (kind) {
            case JETTY:
                return new JettyAppSeverDetector(mavenProject);
            case TOMCAT:
                return new TomcatAppSeverDetector(mavenProject);
            case WILDFLY:
                return new WildFlyAppSeverDetector(mavenProject);
        }

        return null;
    }

    public enum Kind {
        TOMCAT, WILDFLY, JETTY
    }
}
