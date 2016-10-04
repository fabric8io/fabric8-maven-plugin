package io.fabric8.maven.generator.webapp;

import io.fabric8.maven.core.util.MavenUtil;
import org.apache.maven.project.MavenProject;

/**
 * @author kameshs
 */
public class JettyAppSeverDetector extends AbstractAppServerDetector {


    public static final String DOCKER_JETTY_FROM = "fabric8/jetty-9";

    public JettyAppSeverDetector(MavenProject mavenProject) {
        super(mavenProject);
    }

    @Override
    public boolean isApplicable() {
        String[] jettyMagicFiles = new String[]{"**/WEB-INF/jetty-web.xml",
                "**/META-INF/jetty-logging.properties"
        };

        String[] jettyFilesFound = scanFiles(jettyMagicFiles);

        return jettyFilesFound != null && jettyFilesFound.length > 0;
    }

    @Override
    public String getDeploymentDir() {
        return null;
    }

    @Override
    public String getCommand() {
        return "/opt/jetty/bin/deploy-and-run.sh";
    }

    @Override
    public String getFrom() {
        return DOCKER_JETTY_FROM;
    }

    protected boolean hasPlugins() {
        return
                MavenUtil.hasPlugin(project, "org.mortbay.jetty:jetty-maven-plugin")
                        || MavenUtil.hasPlugin(project, "org.eclipse.jetty:jetty-maven-plugin");
    }
}
