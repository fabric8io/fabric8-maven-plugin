package io.fabric8.maven.generator.vertx;

import java.util.Map;

import io.fabric8.maven.core.util.PrefixedLogger;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.fabric8.maven.core.util.FileUtil.getAbsolutePath;
import static org.junit.Assert.assertEquals;

@RunWith(JMockit.class)
public class VertxPortsExtractorTest {

    @Mocked
    MavenProject project;

    @Mocked
    PrefixedLogger log;

    @Mocked
    Plugin plugin;

    @Mocked
    Xpp3Dom configuration;

    @Mocked
    Xpp3Dom vertxConfig;

    @Test
    public void testVertxConfigPathFromProject() throws Exception {
        new Expectations() {{
            project.getPlugin(Constants.VERTX_MAVEN_PLUGIN_GA);
            result = plugin;
            plugin.getConfiguration();
            result = configuration;
            configuration.getChild("config");
            result = vertxConfig;
            vertxConfig.getValue();
            result = getAbsolutePath(VertxPortsExtractorTest.class.getResource("/config.json"));
        }};

        Map<String, Integer> result = new VertxPortsExtractor(log).extract(project);
        assertEquals((Integer) 80, result.get("http.port"));
    }

    @Test
    public void testNoVertxConfiguration() throws Exception {
        new Expectations() {{
            project.getPlugin(Constants.VERTX_MAVEN_PLUGIN_GA);
            plugin.getConfiguration(); result = null;
        }};
        Map<String, Integer> result = new VertxPortsExtractor(log).extract(project);
        assertEquals(0,result.size());
    }
}