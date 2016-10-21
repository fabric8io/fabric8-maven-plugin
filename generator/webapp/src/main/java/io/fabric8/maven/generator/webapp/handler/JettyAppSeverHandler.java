package io.fabric8.maven.generator.webapp.handler;

import io.fabric8.maven.core.util.MavenUtil;
import org.apache.maven.project.MavenProject;

import java.util.Arrays;
import java.util.List;

/**
 * Jetty handler
 *
 * @author kameshs
 */
public class JettyAppSeverHandler extends AbstractAppServerHandler {


    public JettyAppSeverHandler(MavenProject mavenProject) {
        super("jetty", mavenProject);
    }

    @Override
    public boolean isApplicable() {
        return hasOneOf("**/WEB-INF/jetty-web.xml",
                        "**/META-INF/jetty-logging.properties") ||
               MavenUtil.hasPlugin(project, "org.mortbay.jetty:jetty-maven-plugin") ||
               MavenUtil.hasPlugin(project, "org.eclipse.jetty:jetty-maven-plugin");
    }

    @Override
    public String getFrom() {
        return imageLookup.getImageName("jetty.upstream.docker");
    }

    @Override
    public List<String> exposedPorts() {
        return Arrays.asList("8080","8778");
    }

    @Override
    public String getDeploymentDir() {
        return "/deployments";
    }

    @Override
    public String getCommand() {
        return "/opt/jetty/bin/deploy-and-run.sh";
    }

    @Override
    public String getUser() {
        return "jboss:jboss:jboss";
    }
}
