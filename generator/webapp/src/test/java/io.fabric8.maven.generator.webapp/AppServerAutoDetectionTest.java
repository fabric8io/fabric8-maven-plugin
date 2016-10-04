package io.fabric8.maven.generator.webapp;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.Test;

import java.io.File;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

/**
 * @author kameshs
 */
public class AppServerAutoDetectionTest {


    @Test
    public void testIsWildFly() throws Exception {
        String tmpDir = System.getProperty("java.io.tmpdir");
        assertNotNull(tmpDir);

        new File(tmpDir, "WEB-INF/").mkdirs();
        new File(tmpDir, "classes/META-INF").mkdirs();

        File wildFlyDeploymentStruct = new File(tmpDir, "WEB-INF/jboss-deployment-structure.xml");
        wildFlyDeploymentStruct.createNewFile();
        assertNotNull(wildFlyDeploymentStruct);
        assertTrue(wildFlyDeploymentStruct.exists());

        File cdiBeansXml = new File(tmpDir, "classes/META-INF/beans.xml");
        cdiBeansXml.createNewFile();
        assertNotNull(cdiBeansXml);
        assertTrue(cdiBeansXml.exists());

        Model model = new Model();

        Build build = new Build();
        build.setOutputDirectory(tmpDir);

        model.setBuild(build);

        MavenProject mavenProject = new MavenProject(model);
        mavenProject.setBuild(build);
        AppServerDetector appServerDetector = AppServerDetectorFactory
                .getInstance(mavenProject).whichAppKindOfAppServer();
        boolean actual = appServerDetector.isApplicable();
        assertTrue(actual);
    }

    @Test
    public void testIsTomcat() throws Exception {
        String tmpDir = System.getProperty("java.io.tmpdir");
        assertNotNull(tmpDir);

        new File(tmpDir, "classes/META-INF/").mkdirs();

        File tomcatContextResource = new File(tmpDir, "classes/META-INF/context.xml");
        tomcatContextResource.createNewFile();
        assertNotNull(tomcatContextResource);
        assertTrue(tomcatContextResource.exists());

        Model model = new Model();

        Build build = new Build();
        build.setOutputDirectory(tmpDir);

        model.setBuild(build);

        MavenProject mavenProject = new MavenProject(model);
        mavenProject.setBuild(build);
        AppServerDetector appServerDetector = AppServerDetectorFactory
                .getInstance(mavenProject).whichAppKindOfAppServer();
        boolean actual = appServerDetector.isApplicable();
        assertTrue(actual);
    }


    @Test
    public void testIsJetty() throws Exception {
        String tmpDir = System.getProperty("java.io.tmpdir");
        assertNotNull(tmpDir);

        new File(tmpDir, "classes/META-INF/").mkdirs();
        new File(tmpDir, "WEB-INF/").mkdirs();

        File jettyLoggingResource = new File(tmpDir, "classes/jetty-logging.properties");
        jettyLoggingResource.createNewFile();
        assertNotNull(jettyLoggingResource);
        assertTrue(jettyLoggingResource.exists());

        File jettyWebResource = new File(tmpDir, "WEB-INF/jetty-web.xml");
        jettyWebResource.createNewFile();
        assertNotNull(jettyWebResource);
        assertTrue(jettyWebResource.exists());

        Model model = new Model();

        Build build = new Build();
        build.setOutputDirectory(tmpDir);

        model.setBuild(build);

        MavenProject mavenProject = new MavenProject(model);
        mavenProject.setBuild(build);
        AppServerDetector appServerDetector = AppServerDetectorFactory
                .getInstance(mavenProject).whichAppKindOfAppServer();
        boolean actual = appServerDetector.isApplicable();
        assertTrue(actual);
    }


}
