package io.fabric8.maven.generator.api.support;

import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static io.fabric8.maven.generator.api.support.AbstractPortsExtractor.addPortIfValid;
import static io.fabric8.maven.generator.api.support.AbstractPortsExtractor.isValidPortPropertyKey;
import static io.fabric8.maven.generator.api.support.AbstractPortsExtractor.readConfig;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class AbstractPortsExtractorTest {

    @Test
    public void testReadConfigFromJson() throws Exception {
        String jsonConfigPath = decodeUrl(AbstractPortsExtractorTest.class.getResource("/config.json").getFile());
        Map<String, String> map = readConfig(Paths.get(jsonConfigPath).toFile());
        assertThat(map, hasEntry("http.port", "80"));
        assertThat(map, hasEntry("https.port", "443"));

    }

    @Test
    public void testReadConfigFromYaml() throws Exception {
        String jsonConfigPath = decodeUrl(AbstractPortsExtractorTest.class.getResource("/config.yaml").getFile());
        Map<String, String> map = readConfig(Paths.get(jsonConfigPath).toFile());
        assertThat(map, hasEntry("http.port", "80"));
        assertThat(map, hasEntry("https.port", "443"));

    }


    @Test
    public void testReadConfigFromNestedYaml() throws Exception {
        String jsonConfigPath = decodeUrl(AbstractPortsExtractorTest.class.getResource("/config-nested.yaml").getFile());
        Map<String, String> map = readConfig(Paths.get(jsonConfigPath).toFile());
        assertThat(map, hasEntry("http.port", "80"));
        assertThat(map, hasEntry("https.port", "443"));

    }


    @Test
    public void testReadConfigFromProperties() throws Exception {
        String jsonConfigPath = decodeUrl(AbstractPortsExtractorTest.class.getResource("/config.properties").getFile());
        Map<String, String> map = readConfig(Paths.get(jsonConfigPath).toFile());
        assertThat(map, hasEntry("http.port", "80"));
        assertThat(map, hasEntry("https.port", "443"));

    }

    @Test
    public void testIsValidPortPropertyKey() throws Exception {
        assertTrue(isValidPortPropertyKey("web.port"));
        assertTrue(isValidPortPropertyKey("web_port"));
        assertTrue(isValidPortPropertyKey("webPort"));
        assertFalse(isValidPortPropertyKey("ssl.support"));
    }

    @Test
    public void testAddPortToList() {
        Map<String, Integer> l = new HashMap<>();
        addPortIfValid(l, "http.port", "8080");
        addPortIfValid(l, "https.port", "443 ");
        addPortIfValid(l, "ssh.port", " 22");
        addPortIfValid(l, "ssl.enabled", "true");

        assertEquals((Integer)8080, l.get("http.port"));
        assertEquals((Integer)443, l.get("https.port"));
        assertEquals((Integer)22, l.get("ssh.port"));
        assertNull(l.get("ssl.enabled"));
    }


    /**
     * Simple method to decode url.
     * Needed when reading resources via url in environments where path contains url escaped chars (e.g. jenkins workspace).
     * @param url   The url to decode.
     * @return
     */
    private static String decodeUrl(String url) {
        try {
            return URLDecoder.decode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

}