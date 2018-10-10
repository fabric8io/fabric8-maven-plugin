package io.fabric8.maven.enricher.api.util;

import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MavenConfigurationExtractorTest {


    @Test
    public void should_parse_simple_types() {

        // Given
        final Plugin fakePlugin = createFakePlugin("<a>a</a><b>b</b>");

        // When
        final Map<String, Object> config = MavenConfigurationExtractor.extract((Xpp3Dom) fakePlugin.getConfiguration());

        // Then
        assertThat(config)
            .containsEntry("a", "a")
            .containsEntry("b", "b");

    }

    @Test
    public void should_parse_inner_objects() {

        // Given
        final Plugin fakePlugin = createFakePlugin("<a>"
            + "<b>b</b>"
            + "</a>");

        // When
        final Map<String, Object> config = MavenConfigurationExtractor.extract((Xpp3Dom) fakePlugin.getConfiguration());

        // Then
        final Map<String, Object> expected = new HashMap<>();
        expected.put("b", "b");
        assertThat(config)
            .containsEntry("a", expected);

    }

    @Test
    public void should_parse_list_of_elements() {

        // Given
        final Plugin fakePlugin = createFakePlugin("<a>"
            + "<b>"
            + "<c>c1</c><c>c2</c>"
            + "</b>"
            + "</a>");

        // When
        final Map<String, Object> config = MavenConfigurationExtractor.extract((Xpp3Dom) fakePlugin.getConfiguration());

        // Then
        final Map<String, Object> expectedC = new HashMap<>();
        expectedC.put("c", Arrays.asList("c1", "c2"));
        final Map<String, Object> expected = new HashMap<>();
        expected.put("b", expectedC);

        assertThat(config)
            .containsEntry("a",expected);

    }

    private Plugin createFakePlugin(String config) {
        Plugin plugin = new Plugin();
        plugin.setArtifactId("fabric8-maven-plugin");
        plugin.setGroupId("io.fabric8");
        String content = "<configuration>"
            + config
            + "</configuration>";
        Xpp3Dom dom;
        try {
            dom = Xpp3DomBuilder.build(new StringReader(content));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        plugin.setConfiguration(dom);

        return plugin;
    }
}
