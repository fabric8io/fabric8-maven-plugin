package io.fabric8.maven.generator.webapp.handler;

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
        super(project);
    }

    @Override
    public boolean isApplicable() {
        return hasOneOf("**/META-INF/context.xml");
    }

    @Override
    public String getFrom() {
        return imageLookup.getImageName("image.tomcat.upstream");
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
