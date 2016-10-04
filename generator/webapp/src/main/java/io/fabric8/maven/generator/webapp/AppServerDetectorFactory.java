package io.fabric8.maven.generator.webapp;

import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;

/**
 * @author kameshs
 */
public class AppServerDetectorFactory {

    public static AppServerDetectorFactory instance;

    private final List<AppServerDetector> serverDetectors = new ArrayList<>();
    AppServerDetector appServerDetector;
    private MavenProject mavenProject;

    private AppServerDetectorFactory(MavenProject mavenProject) {

        this.mavenProject = mavenProject;

        for (AppServerDetectorFactory.Kind k : AppServerDetectorFactory.Kind.values()) {
            serverDetectors.add(getAppServerDetector(k));
        }
    }

    public static AppServerDetectorFactory getInstance(MavenProject mavenProject) {
        if (instance == null) {
            instance = new AppServerDetectorFactory(mavenProject);
        }
        return instance;
    }

    public AppServerDetector getAppServerDetector(final Kind kind) {

        switch (kind) {
            case JETTY:
                return new JettyAppSeverDetector(mavenProject);
            case WILDFLY:
                return new WildFlyAppSeverDetector(mavenProject);
            case TOMCAT:
            default:
                return new TomcatAppSeverDetector(mavenProject);
        }
    }

    public AppServerDetector whichAppKindOfAppServer() {
        if (appServerDetector == null) {
            for (AppServerDetector appServerDetector : serverDetectors) {
                if (appServerDetector.isApplicable()) {
                    this.appServerDetector = appServerDetector;
                    break;
                }
            }
        }
        return appServerDetector;
    }

    public enum Kind {
        TOMCAT, WILDFLY, JETTY
    }
}
