package io.fabric8.maven.generator.webapp.handler;

import io.fabric8.maven.core.util.MavenUtil;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Detector for tomat app servers.
 *
 * @author kameshs
 */
public class TomcatAppSeverHandler extends AbstractAppServerHandler {

    public TomcatAppSeverHandler(MavenProject project) {
        super("tomcat", project);
    }

    @Override
    public boolean isApplicable() {
        return hasOneOf("**/META-INF/context.xml") ||
                MavenUtil.hasPlugin(project, "org.apache.tomcat.maven:tomcat6-maven-plugin") ||
                MavenUtil.hasPlugin(project, "org.apache.tomcat.maven:tomcat7-maven-plugin");
    }

    @Override
    public String getFrom() {
        return imageLookup.getImageName("tomcat.upstream.docker");
    }

    @Override
    public List<String> exposedPorts() {
        return Arrays.asList("8080", "8778");
    }

    @Override
    public String getDeploymentDir() {
        return "/deployments";
    }

    @Override
    public String getCommand() {
        return "/opt/tomcat/bin/deploy-and-run.sh";
    }

    @Override
    public String getUser() {
        return "jboss:jboss:jboss";
    }
}
