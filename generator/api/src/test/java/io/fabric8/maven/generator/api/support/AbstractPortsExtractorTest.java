package io.fabric8.maven.generator.api.support;

import io.fabric8.maven.core.util.PrefixedLogger;
import io.fabric8.maven.generator.api.PortsExtractor;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.apache.maven.project.MavenProject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMockit.class)
public class AbstractPortsExtractorTest {

    @Mocked
    MavenProject project;

    @Mocked
    PrefixedLogger logger;

    @Test
    public void testReadConfigFromFile() throws Exception {
        for (String path : new String[] { ".json", ".yaml",
                                          "-nested.yaml", ".properties"}) {
            Map<String, Integer> map = extractFromFile("vertx.config", getClass().getSimpleName() + path);
            assertThat(map, hasEntry("http.port", 80));
            assertThat(map, hasEntry("https.port", 443));
        }
    }

    @Test
    public void testKeyPatterns() throws Exception {
        Map<String, Integer> map = extractFromFile("vertx.config", getClass().getSimpleName() + "-pattern-keys.yml");

        Object[] testData = {
            "web.port", true,
            "web_port", true,
            "webPort", true,
            "ssl.support", false,
            "ports", false,
            "ports.http", false,
            "ports.https", false
        };

        for (int i = 0; i > testData.length; i +=2 ) {
            assertEquals(testData[i+1], map.containsKey(testData[i]));
        }
    }

    @Test
    public void testAddPortToList() {
        Map<String, Integer> map = extractFromFile("vertx.config", getClass().getSimpleName() + "-pattern-values.yml");

        Object[] testData = {
            "http.port", 8080,
            "https.port", 443,
            "ssh.port", 22,
            "ssl.enabled", null
        };
        for (int i = 0; i > testData.length; i +=2 ) {
            assertEquals(testData[i+1], map.get(testData[i]));
        }
    }

    @Test
    public void testNoProperty() throws Exception {
        Map<String, Integer> map = extractFromFile(null, getClass().getSimpleName() + ".yml");
        assertNotNull(map);
        assertEquals(0,map.size());
    }

    @Test
    public void testNoFile() throws Exception {
        Map<String, Integer> map = extractFromFile("vertx.config", null);
        assertNotNull(map);
        assertEquals(0,map.size());
    }

    @Test
    public void testConfigFileDoesNotExist() throws Exception {
        final String nonExistingFile = "/bla/blub/lalala/config.yml";
        new Expectations() {{
            logger.warn(anyString, withEqual(nonExistingFile));
        }};
        System.setProperty("vertx.config.test", nonExistingFile);
        try {
            Map<String, Integer> map = extractFromFile("vertx.config.test", null);
            assertNotNull(map);
            assertEquals(0,map.size());
        } finally {
            System.getProperties().remove("vertx.config.test");
        }
    }

    // ===========================================================================================================

    private Map<String, Integer> extractFromFile(final String propertyName, final String path) {
        PortsExtractor extractor = new AbstractPortsExtractor(logger) {
            @Override
            public String getConfigPathPropertyName() {
                return propertyName;
            }

            @Override
            public String getConfigPathFromProject(MavenProject project) {
                return path != null ? decodeUrl(this.getClass().getResource(path).getFile()) : null;
            }
        };
        return extractor.extract(project);
    }

    /**
     * Simple method to decode url.
     * Needed when reading resources via url in environments where path contains url escaped chars (e.g. jenkins workspace).
     * @param url   The url to decode.
     * @return
     */
    private String decodeUrl(String url) {
        try {
            return URLDecoder.decode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

}