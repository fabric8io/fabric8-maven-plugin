package io.fabric8.maven.generator.vertx;

import java.io.File;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableSet;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.generator.api.GeneratorContext;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@RunWith(JMockit.class)
public class VertxGeneratorTest {

    @Injectable
    private Logger logger;

    private DefaultArtifactHandler handler = new DefaultArtifactHandler("jar");


    private final Artifact dropwizard = new DefaultArtifact("io.vertx", "vertx-dropwizard-metrics", "3.4.2", null, "jar", "", null);
    private final Artifact core = new DefaultArtifact("io.vertx", "vertx-core", "3.4.2", null, "jar", "", null);
    private final Artifact infinispan = new DefaultArtifact("io.vertx", "vertx-infinispan", "3.4.2", null, "jar", "", handler);


    @Test
    public void testDefaultOptions(@Mocked final MavenProject project) {
        new Expectations() {{
            project.getBuild().getDirectory(); result = new File("target/tmp").getAbsolutePath();
            project.getBuild().getOutputDirectory(); result = new File("target/tmp/target").getAbsolutePath();
        }};

        GeneratorContext context = new GeneratorContext.Builder()
                .logger(logger)
                .project(project)
                .build();
        VertxGenerator generator = new VertxGenerator(context);
        List<String> list = generator.getExtraJavaOptions();

        assertThat(list).containsOnly("-Dvertx.cacheDirBase=/tmp", "-Dvertx.disableDnsResolver=true");
    }

    @Test
    public void testWithMetrics(@Mocked final MavenProject project) {

        new Expectations() {{
            project.getBuild().getDirectory(); result = new File("target/tmp").getAbsolutePath();
            project.getBuild().getOutputDirectory(); result = new File("target/tmp/target").getAbsolutePath();
            project.getArtifacts(); result = ImmutableSet.of(dropwizard, core);
        }};

        GeneratorContext context = new GeneratorContext.Builder()
                .logger(logger)
                .project(project)
                .build();
        VertxGenerator generator = new VertxGenerator(context);
        List<String> list = generator.getExtraJavaOptions();

        assertThat(list).containsOnly(
                // Default entries
                "-Dvertx.cacheDirBase=/tmp", "-Dvertx.disableDnsResolver=true",
                // Metrics entries
                "-Dvertx.metrics.options.enabled=true", "-Dvertx.metrics.options.jmxEnabled=true", "-Dvertx.metrics.options.jmxDomain=vertx");
    }

    @Test
    public void testWithInfinispanClusterManager(@Mocked final MavenProject project) throws MojoExecutionException {
        new Expectations() {{
            project.getBuild().getDirectory(); result = new File("target/tmp").getAbsolutePath();
            project.getBuild().getOutputDirectory(); result = new File("target/tmp/target").getAbsolutePath();
            project.getArtifacts(); result = ImmutableSet.of(infinispan, core);
        }};

        GeneratorContext context = new GeneratorContext.Builder()
                .logger(logger)
                .project(project)
                .build();
        VertxGenerator generator = new VertxGenerator(context);
        Map<String, String> env = generator.getEnv(true);

        assertThat(env).contains(entry("JAVA_OPTIONS", "-Dvertx.cacheDirBase=/tmp -Dvertx.disableDnsResolver=true " +
                // Force IPv4
                "-Djava.net.preferIPv4Stack=true"));
        assertThat(env).contains(entry("JAVA_ARGS", "-cluster"));
    }


}