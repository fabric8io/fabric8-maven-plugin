package io.fabric8.maven.generator.webapp;

import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;

/**
 * @author kameshs
 */
public class TomcatAppSeverDetector extends AbstractAppServerDetector {

    public static final String DOCKER_FROM = "fabric8/tomcat-8";

    public TomcatAppSeverDetector(MavenProject project) {
        super(project);
    }

    @Override
    public List<String> exposedPorts() {
        List<String> ports = new ArrayList<>();
        ports.add("8080");
        ports.add("8778");
        return ports;
    }

    @Override
    public AppServerDetectorFactory.Kind getKind() {
        return AppServerDetectorFactory.Kind.TOMCAT;
    }

    @Override
    public boolean isApplicable() {
        String[] tomcatFilesFound = scanFiles("**/META-INF/context.xml");
        return tomcatFilesFound != null && tomcatFilesFound.length > 0;
    }

    @Override
    public String getDeploymentDir() {
        return "/deployments";
    }

    @Override
    public String getCommand() {
        return null;
    }

    @Override
    public String getFrom() {
        return DOCKER_FROM;
    }

    @Override
    protected boolean hasPlugins() {
        return true;
    }
}
