package io.fabric8.maven.generator.webapp;

import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.DirectoryScanner;

/**
 * @author kameshs
 */
public class AppSeverDetector {

    public static boolean hasTomcatFiles(MavenProject project) {

        String[] tomcatFilesFound = scanFiles(project, "**/META-INF/context.xml");

        return tomcatFilesFound != null && tomcatFilesFound.length > 0;
    }

    private static String[] scanFiles(MavenProject project, String... pattern) {
        String buildOutputDir =
                project.getBuild().getOutputDirectory();
        DirectoryScanner directoryScanner = new DirectoryScanner();
        directoryScanner.setBasedir(buildOutputDir);
        directoryScanner.setIncludes(pattern);
        directoryScanner.scan();
        return directoryScanner.getIncludedFiles();
    }

    public static boolean hasJettyFiles(MavenProject project) {

        String[] jettyMagicFiles = new String[]{"**/WEB-INF/jetty-web.xml",
                "**/META-INF/jetty-logging.properties"
        };

        String[] jettyFilesFound = scanFiles(project, jettyMagicFiles);

        return jettyFilesFound != null && jettyFilesFound.length > 0;
    }

    public static boolean hasWildFlyFiles(MavenProject project) {
        String[] wildFlyMagicFiles = new String[]{"**/WEB-INF/jboss-deployment-structure.xml",
                "**/META-INF/jboss-deployment-structure.xml",
                "**/WEB-INF/beans.xml", "**/META-INF/beans.xml",
                "**/WEB-INF/jboss-web.xml", "**/WEB-INF/ejb-jar.xml",
                "**/WEB-INF/jboss-ejb3.xml", "**/META-INF/persistence.xml",
                "**/META-INF/*-jms.xml", "**/WEB-INF/*-jms.xml",
                "**/META-INF/*-ds.xml", "**/WEB-INF/*-ds.xml",
                "**/WEB-INF/jboss-ejb-client.xml", "**/META-INF/jbosscmp-jdbc.xml",
                "**/WEB-INF/jboss-webservices.xml"
        };

        String[] wildFlyFilesFound = scanFiles(project, wildFlyMagicFiles);

        return wildFlyFilesFound != null && wildFlyFilesFound.length > 0;
    }

}
