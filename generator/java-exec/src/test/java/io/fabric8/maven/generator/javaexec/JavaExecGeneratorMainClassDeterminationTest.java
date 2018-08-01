package io.fabric8.maven.generator.javaexec;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;

import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.util.ClassUtil;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.generator.api.GeneratorContext;
import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Checking how the JavaExecGenerator checks the need to set a main class as environment variable JAVA_MAIN_CLASS
 * in various situations
 *
 * @author: Oliver Weise
 * @since: 2016-11-30
 */
@RunWith(JMockit.class)
public class JavaExecGeneratorMainClassDeterminationTest {

    @Mocked
    Logger log;

    // Only to mock unwanted fatjar directory functionality
    public static class MockJavaExecGenerator extends MockUp<JavaExecGenerator> {

        @Mock
        protected void addAssembly(AssemblyConfiguration.Builder builder) throws MojoExecutionException {

        }

    }

    public static class MockFatJarDetector extends MockUp<FatJarDetector> {

        private final boolean findClass;

        public MockFatJarDetector(boolean findClass) {
            this.findClass = findClass;
        }

        @Mock
        FatJarDetector.Result scan(Invocation invocation) {

            if (!findClass) {
                return null;
            }

            FatJarDetector detector = invocation.getInvokedInstance();
            return detector.new Result(
                    new File("/the/archive/file"),
                    "the.fatjar.main.ClassName",
                    new Attributes()
            );
        }

    }

    public static class MockBuild extends MockUp<Build> {

        @Mock
        public String getDirectory() {
            return "/the/directory";
        }

        @Mock
        public String getOutputDirectory() {
            return "/the/output/directory";
        }

    }

    public static class MockMavenProject extends MockUp<MavenProject> {

        @Mock
        public Build getBuild() {

            return new Build();
        }

    }

    public static class MockClassUtils extends MockUp<ClassUtil> {

        @Mock
        public static List<String> findMainClasses(File rootDir) throws IOException {
            return Collections.singletonList("the.detected.MainClass");
        }

    }

    public static class MockProcessorConfig extends MockUp<ProcessorConfig> {

        private final String mainClassName;

        public MockProcessorConfig(String s) {
            this.mainClassName = s;
        }

        @Mock
        public String getConfig(String name, String key) {
            if ("java-exec".equals(name)) {
                if (JavaExecGenerator.Config.mainClass.toString().equals(key)) {
                    return this.mainClassName;
                }
                if ("name".equals(key)) {
                    return "TheImageName";
                }
                if ("webPort".equals(key)) {
                    return "8080";
                }
                if ("jolokiaPort".equals(key)) {
                    return "1234";
                }
                if ("prometheusPort".equals(key)) {
                    return "2345";
                }
                if ("targetDir".equals(key)) {
                    return "/the/target/dir";
                }
            }

            return null;

        }
    }

    /**
     * The main class is determined via config in a non-fat-jar deployment
     * @throws MojoExecutionException
     */
    @Test
    public void testMainClassDeterminationFromConfig() throws MojoExecutionException {

        new MockBuild();
        new MockProcessorConfig("the.main.ClassName");
        new MockMavenProject();

        final GeneratorContext generatorContext = new GeneratorContext.Builder()
                .project(new MavenProject())
                .config(new ProcessorConfig())
                .strategy(OpenShiftBuildStrategy.docker)
                .logger(log)
                .build();

        JavaExecGenerator generator = new JavaExecGenerator(generatorContext);

        final List<ImageConfiguration> images = new ArrayList<ImageConfiguration>();

        List<ImageConfiguration> customized = generator.customize(images, false);

        assertEquals("1 images returned", (long) 1, (long) customized.size());

        ImageConfiguration imageConfig = customized.get(0);

        assertEquals("Image name", "TheImageName", imageConfig.getName());
        assertEquals("Main Class set as environment variable",
                "the.main.ClassName",
                imageConfig.getBuildConfiguration().getEnv().get(JavaExecGenerator.JAVA_MAIN_CLASS_ENV_VAR));


    }

    /**
     * The main class is determined via main class detection in a non-fat-jar deployment
     * @throws MojoExecutionException
     */
    @Test
    public void testMainClassDeterminationFromDetectionOnNonFatJar() throws MojoExecutionException {

        new MockBuild();
        new MockProcessorConfig(null);
        new MockMavenProject();
        new MockFatJarDetector(false);
        new MockClassUtils();

        final GeneratorContext generatorContext = new GeneratorContext.Builder()
                .project(new MavenProject())
                .config(new ProcessorConfig())
                .strategy(OpenShiftBuildStrategy.docker)
                .logger(log)
                .build();

        JavaExecGenerator generator = new JavaExecGenerator(generatorContext);

        final List<ImageConfiguration> images = new ArrayList<ImageConfiguration>();

        List<ImageConfiguration> customized = generator.customize(images, false);

        assertEquals("1 images returned", (long) 1, (long) customized.size());

        ImageConfiguration imageConfig = customized.get(0);

        assertEquals("Image name", "TheImageName", imageConfig.getName());
        assertEquals("Main Class set as environment variable",
                "the.detected.MainClass",
                imageConfig.getBuildConfiguration().getEnv().get(JavaExecGenerator.JAVA_MAIN_CLASS_ENV_VAR));


    }

    /**
     * The main class is determined as the Main-Class of a fat jar
     * @throws MojoExecutionException
     */
    @Test
    public void testMainClassDeterminationFromFatJar() throws MojoExecutionException {

        new MockBuild();
        new MockProcessorConfig(null);
        new MockMavenProject();
        new MockFatJarDetector(true);
        new MockJavaExecGenerator();

        final GeneratorContext generatorContext = new GeneratorContext.Builder()
                .project(new MavenProject())
                .config(new ProcessorConfig())
                .strategy(OpenShiftBuildStrategy.docker)
                .logger(log)
                .build();


        JavaExecGenerator generator = new JavaExecGenerator(generatorContext);

        final List<ImageConfiguration> images = new ArrayList<ImageConfiguration>();

        List<ImageConfiguration> customized = generator.customize(images, false);

        assertEquals("1 images returned", (long) 1, (long) customized.size());

        ImageConfiguration imageConfig = customized.get(0);

        assertEquals("Image name", "TheImageName", imageConfig.getName());
        assertNull("Main Class is NOT set as environment variable#",
                imageConfig.getBuildConfiguration().getEnv().get(JavaExecGenerator.JAVA_MAIN_CLASS_ENV_VAR));


    }


}
