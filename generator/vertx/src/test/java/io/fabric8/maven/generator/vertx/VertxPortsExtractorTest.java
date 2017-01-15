package io.fabric8.maven.generator.vertx;

import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Map;

import io.fabric8.maven.core.util.PrefixedLogger;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;

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
            configuration.getChild(Constants.CONFIG);
            result = vertxConfig;
            vertxConfig.getValue();
            result = decodeUrl(VertxPortsExtractorTest.class.getResource("/config.json").getFile());
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

    /**
     * Simple method to decode url. Needed when reading resources via url in environments where path
     * contains url escaped chars (e.g. jenkins workspace).
     *
     * @param url The url to decode.
     */
    private static String decodeUrl(String url) {
        try {
            return URLDecoder.decode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

}