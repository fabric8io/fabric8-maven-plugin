package io.fabric8.maven.generator.webapp;

import org.apache.maven.project.MavenProject;

/**
 * @author kameshs
 */
public class TomcatAppSeverDetector extends AbstractAppServerDetector {

    public static final String DOCKER_TOMCAT_FROM = "fabric8/tomcat-8";

    public TomcatAppSeverDetector(MavenProject project) {
        super(project);
    }

    @Override
    public boolean isApplicable() {
        String[] tomcatFilesFound = scanFiles("**/META-INF/context.xml");
        return tomcatFilesFound != null && tomcatFilesFound.length > 0;
    }

    @Override
    public String getDeploymentDir() {
        return null;
    }

    @Override
    public String getCommand() {
        return null;
    }

    @Override
    public String getFrom() {
        return DOCKER_TOMCAT_FROM;
    }

    @Override
    protected boolean hasPlugins() {
        return true;
    }
}
