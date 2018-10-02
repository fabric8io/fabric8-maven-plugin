package io.fabric8.maven.core.util;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertiesMappingParserTest {

    private static final String MAPPING_PROPERTIES = "ConfigMap=cm, configmap";

    @Test
    public void should_read_mappings_from_properties_file() {

        // Given

        final PropertiesMappingParser propertiesMappingParser = new PropertiesMappingParser();

        // When

        final Map<String, List<String>> serializedContent =
            propertiesMappingParser.parse(new ByteArrayInputStream(MAPPING_PROPERTIES.getBytes()));

        // Then

        final Map<String, List<String>> expectedSerlializedContent = new HashMap<>();
        expectedSerlializedContent.put("ConfigMap", Arrays.asList("cm", "configmap"));

        assertThat(serializedContent)
            .containsAllEntriesOf(expectedSerlializedContent);
    }

}
