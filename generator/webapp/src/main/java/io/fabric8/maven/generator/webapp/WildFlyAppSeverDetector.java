package io.fabric8.maven.generator.webapp;

import io.fabric8.maven.core.util.MavenUtil;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.DirectoryScanner;

/**
 * @author kameshs
 */
public class WildFlyAppSeverDetector extends AbstractAppServerDetector{

    private static final String DOCKER_WILDFLY_FROM = "jboss/wildfly";
    public static final String WILDFLY_DEPLOYMENTS_DIR = "/opt/jboss/wildfly/standalone/deployments";
    public static final String WILDFLY_RUN_COMMAND = "/opt/jboss/wildfly/bin/standalone.sh";

    public WildFlyAppSeverDetector(MavenProject project) {
        super(project);
    }

    protected boolean hasPlugins(){
        return
                MavenUtil.hasPlugin(project, "org.jboss.as.plugins:jboss-as-maven-plugin")
                        || MavenUtil.hasPlugin(project, "org.wildfly.plugins:wildfly-maven-plugin");
    }


    @Override
    public boolean isApplicable() {
        final String[] wildFlyMagicFiles = new String[]{"**/WEB-INF/jboss-deployment-structure.xml",
                "**/META-INF/jboss-deployment-structure.xml",
                "**/WEB-INF/beans.xml", "**/META-INF/beans.xml",
                "**/WEB-INF/jboss-web.xml", "**/WEB-INF/ejb-jar.xml",
                "**/WEB-INF/jboss-ejb3.xml", "**/META-INF/persistence.xml",
                "**/META-INF/*-jms.xml", "**/WEB-INF/*-jms.xml",
                "**/META-INF/*-ds.xml", "**/WEB-INF/*-ds.xml",
                "**/WEB-INF/jboss-ejb-client.xml", "**/META-INF/jbosscmp-jdbc.xml",
                "**/WEB-INF/jboss-webservices.xml"
        };

        String[] wildFilesFound = scanFiles(wildFlyMagicFiles);
        return hasPlugins() || (wildFilesFound != null && wildFilesFound.length > 0);
    }

    @Override
    public String getDeploymentDir() {
        return WILDFLY_DEPLOYMENTS_DIR;
    }

    @Override
    public String getCommand() {
        return WILDFLY_RUN_COMMAND;
    }

    @Override
    public String getFrom() {
        return DOCKER_WILDFLY_FROM;
    }
}
