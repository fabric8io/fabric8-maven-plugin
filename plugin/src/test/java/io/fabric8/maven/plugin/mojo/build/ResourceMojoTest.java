package io.fabric8.maven.plugin.mojo.build;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.core.config.ConfigMapEntry;
import io.fabric8.maven.core.config.ResourceConfig;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceMojoTest {

    @Test
    public void should_add_config_map_content_from_configuration() {

        // Given
        io.fabric8.maven.core.config.ConfigMap mockedConfigMap = new io.fabric8.maven.core.config.ConfigMap();

        final ConfigMapEntry standardConfigMapEntry = new ConfigMapEntry();
        standardConfigMapEntry.setName("A");
        standardConfigMapEntry.setValue("B");
        mockedConfigMap.addElement(standardConfigMapEntry);

        final ConfigMapEntry fileConfigMapEntry = new ConfigMapEntry();
        fileConfigMapEntry.setFile("src/test/resources/test-application.properties");
        mockedConfigMap.addElement(fileConfigMapEntry);

        final ResourceConfig.Builder resourceConfigBuilder = new ResourceConfig.Builder();
        resourceConfigBuilder.withConfigMap(mockedConfigMap);

        final KubernetesListBuilder builder = new KubernetesListBuilder();
        ResourceMojo resourceMojo = new ResourceMojo();

        // When
        resourceMojo.addConfigMapFromConfigurations(builder, mockedConfigMap);

        // Then
        final ConfigMap configMap = (ConfigMap) builder.buildFirstItem();

        final Map<String, String> data = configMap.getData();

        assertThat(data)
            .containsKey("test-application.properties")
            .containsKey("A");

    }

}
