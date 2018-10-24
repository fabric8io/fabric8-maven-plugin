package io.fabric8.maven.enricher.standard;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.maven.core.config.ConfigMapEntry;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.model.Configuration;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import java.util.HashMap;
import java.util.Map;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JMockit.class)
public class ConfigMapEnricherTest {

    @Mocked
    private MavenEnricherContext context;

    @Test
    public void should_materialize_file_content_from_annotation() {

        // Given

        new Expectations() {
            {{
                context.getConfiguration();
                result = new Configuration.Builder()
                    .resource(new ResourceConfig())
                    .build();
            }}

        };

        final ConfigMapEnricher configMapEnricher =
            new ConfigMapEnricher(context);
        final KubernetesListBuilder builder = new KubernetesListBuilder();
        builder.addToConfigMapItems(createBaseConfigMap());

        // When
        configMapEnricher.addMissingResources(builder);

        // Then
        final ConfigMap configMap = (ConfigMap) builder.buildFirstItem();

        final Map<String, String> data = configMap.getData();
        assertThat(data)
            .containsKey("test-application.properties");

        final Map<String, String> annotations = configMap.getMetadata().getAnnotations();
        assertThat(annotations)
            .isEmpty();
    }

    private ConfigMap createBaseConfigMap() {
        ObjectMetaBuilder metaBuilder = new ObjectMetaBuilder()
            .withNamespace("default");

        Map<String, String> annotations = new HashMap<>();
        annotations.put(ConfigMapEnricher.PREFIX_ANNOTATION + "test-application.properties",
            "src/test/resources/test-application.properties");
        metaBuilder = metaBuilder.withAnnotations(annotations);

        Map<String, String> data = new HashMap<>();
        return new ConfigMapBuilder()
            .withData(data)
            .withMetadata(metaBuilder.build())
            .build();
    }
}
