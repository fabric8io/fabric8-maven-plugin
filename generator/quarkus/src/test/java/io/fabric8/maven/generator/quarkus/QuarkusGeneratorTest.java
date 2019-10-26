/**
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.maven.generator.quarkus;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertNotNull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;
import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.config.RuntimeMode;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.generator.api.GeneratorContext;
import mockit.Expectations;
import mockit.Mocked;

/**
 * @author jzuriaga
 */
public class QuarkusGeneratorTest {

    private static final String QUARKUS_GROUP = "io.quarkus";
    private static final String QUARKUS_MAVEN_PLUGIN = "quarkus-maven-plugin";
    private final Plugin quarkusPlugin = new Plugin();

    private static final String BASE_JAVA_IMAGE = "java:latest";
    private static final String BASE_NATIVE_IMAGE = "fedora:latest";

    @Mocked
    private GeneratorContext ctx;

    @Mocked
    private MavenProject project;

    @Mocked
    private ProcessorConfig config;
    
    private Properties projectProps = new Properties();

    @Before
    public void setUp () throws IOException {
        createFakeRunnerJar();
        // @formatter:off
        new Expectations() {{
            project.getVersion(); result = "0.0.1-SNAPSHOT";
            project.getBuild().getDirectory(); result = new File("target/tmp").getAbsolutePath();
            // project.getPlugin(QUARKUS_GROUP + ":" + QUARKUS_MAVEN_PLUGIN); result = quarkusPlugin;
        }};
        // @formatter:on
        projectProps.put("fabric8.generator.name", "quarkus");
        setupContextOpenShift(projectProps, null, null);
    }

    @Test
    public void testCustomizeReturnsDefaultFrom () throws MojoExecutionException {
        QuarkusGenerator generator = new QuarkusGenerator(ctx);
        List<ImageConfiguration> resultImages = null;
        List<ImageConfiguration> existingImages = new ArrayList<>();

        resultImages = generator.customize(existingImages, true);

        assertBuildFrom(resultImages, "openjdk:11");
    }

    @Test
    public void testCustomizeReturnsDefaultFromWhenNative () throws MojoExecutionException, IOException {
        setNativeConfig();

        QuarkusGenerator generator = new QuarkusGenerator(ctx);
        List<ImageConfiguration> resultImages = null;
        List<ImageConfiguration> existingImages = new ArrayList<>();

        resultImages = generator.customize(existingImages, true);

        assertBuildFrom(resultImages, "registry.fedoraproject.org/fedora-minimal");
    }

    @Test
    public void testCustomizeReturnsConfiguredFrom () throws MojoExecutionException {
        // @formatter:off
        new Expectations() {{
            config.getConfig("quarkus", "from"); result = BASE_JAVA_IMAGE;
        }};
        // @formatter:on
        
        QuarkusGenerator generator = new QuarkusGenerator(ctx);
        List<ImageConfiguration> resultImages = null;
        List<ImageConfiguration> existingImages = new ArrayList<>();

        resultImages = generator.customize(existingImages, true);

        assertBuildFrom(resultImages, BASE_JAVA_IMAGE);
    }

    @Test
    public void testCustomizeReturnsConfiguredFromWhenNative () throws MojoExecutionException, IOException {
        setNativeConfig();
        // @formatter:off
        new Expectations() {{
            config.getConfig("quarkus", "from"); result = BASE_NATIVE_IMAGE;
        }};
        // @formatter:on

        QuarkusGenerator generator = new QuarkusGenerator(ctx);
        List<ImageConfiguration> resultImages = null;
        List<ImageConfiguration> existingImages = new ArrayList<>();

        resultImages = generator.customize(existingImages, true);

        assertBuildFrom(resultImages, BASE_NATIVE_IMAGE);
    }

    @Test
    public void testCustomizeReturnsPropertiesFrom () throws MojoExecutionException {
        projectProps.put("fabric8.generator.quarkus.from", BASE_JAVA_IMAGE);

        QuarkusGenerator generator = new QuarkusGenerator(ctx);
        List<ImageConfiguration> resultImages = null;
        List<ImageConfiguration> existingImages = new ArrayList<>();

        resultImages = generator.customize(existingImages, true);

        assertBuildFrom(resultImages, BASE_JAVA_IMAGE);
    }


    @Test
    public void testCustomizeReturnsPropertiesFromWhenNative () throws MojoExecutionException, IOException {
        setNativeConfig();
        projectProps.put("fabric8.generator.quarkus.from", BASE_NATIVE_IMAGE);

        QuarkusGenerator generator = new QuarkusGenerator(ctx);
        List<ImageConfiguration> resultImages = null;
        List<ImageConfiguration> existingImages = new ArrayList<>();

        resultImages = generator.customize(existingImages, true);

        assertBuildFrom(resultImages, BASE_NATIVE_IMAGE);
    }

    private void assertBuildFrom (List<ImageConfiguration> resultImages, String baseImage) {
        assertNotNull(resultImages);
        assertThat(resultImages, hasSize(1));
        assertThat(resultImages,
                hasItem(hasProperty("buildConfiguration", hasProperty("from", equalTo(baseImage)))));
    }

    private void setupContextOpenShift (final Properties projectProps, final String configFrom,
            final String configFromMode) {
        // @formatter:off
        new Expectations() {{
            ctx.getProject(); result = project;
            project.getProperties(); result = projectProps;
            ctx.getConfig(); result = config;
            config.getConfig("test-generator", "from"); result = configFrom; minTimes = 0;
            config.getConfig("test-generator", "fromMode"); result = configFromMode; minTimes = 0;
            ctx.getRuntimeMode(); result = RuntimeMode.openshift; minTimes = 0;
            ctx.getStrategy(); result = OpenShiftBuildStrategy.s2i; minTimes = 0;
        }};
        // @formatter:on
    }

    private void createFakeRunnerJar () throws IOException {
        File baseDir = createBaseDir();
        File runnerJar = new File(baseDir, "sample-runner.jar");
        runnerJar.createNewFile();
    }

    private File createBaseDir () {
        File baseDir = new File("target", "tmp");
        baseDir.mkdir();
        return baseDir;
    }

    private void setNativeConfig () throws IOException {
        createFakeNativeImage();
        projectProps.put("fabric8.generator.quarkus.nativeImage", "true");
    }

    private void createFakeNativeImage () throws IOException {
        File baseDir = createBaseDir();
        File runnerExec = new File(baseDir, "sample-runner");
        runnerExec.createNewFile();
    }

}
