package io.fabric8.maven.generator.webapp.handler;

import io.fabric8.maven.core.util.MavenUtil;
import org.apache.maven.project.MavenProject;

import java.util.Arrays;
import java.util.List;

/**
 * Handler for wildfly
 *
 * @author kameshs
 */
public class WildFlyAppSeverHandler extends AbstractAppServerHandler {

    public WildFlyAppSeverHandler(MavenProject project) {
        super("wildfly", project);
    }

    @Override
    public boolean isApplicable() {
        return
            hasOneOf("**/WEB-INF/jboss-deployment-structure.xml",
                     "**/META-INF/jboss-deployment-structure.xml",
                     "**/WEB-INF/beans.xml", "**/META-INF/beans.xml",
                     "**/WEB-INF/jboss-web.xml", "**/WEB-INF/ejb-jar.xml",
                     "**/WEB-INF/jboss-ejb3.xml", "**/META-INF/persistence.xml",
                     "**/META-INF/*-jms.xml", "**/WEB-INF/*-jms.xml",
                     "**/META-INF/*-ds.xml", "**/WEB-INF/*-ds.xml",
                     "**/WEB-INF/jboss-ejb-client.xml", "**/META-INF/jbosscmp-jdbc.xml",
                     "**/WEB-INF/jboss-webservices.xml") ||
            MavenUtil.hasPlugin(project, "org.jboss.as.plugins:jboss-as-maven-plugin") ||
            MavenUtil.hasPlugin(project, "org.wildfly.plugins:wildfly-maven-plugin");
    }

    @Override
    public String getFrom() {
        return imageLookup.getImageName("wildfly.upstream.docker");
    }

    @Override
    public List<String> exposedPorts() {
        return Arrays.asList("8080");
    }

    @Override
    public String getDeploymentDir() {
        return "/opt/jboss/wildfly/standalone/deployments";
    }

    @Override
    public String getCommand() {
        return "/opt/jboss/wildfly/bin/standalone.sh";
    }

    @Override
    public String getUser() {
        return "jboss:jboss:jboss";
    }
}
