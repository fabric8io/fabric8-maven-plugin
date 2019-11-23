/**
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.maven.enricher.standard;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.maven.core.config.ConfigMapEntry;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.model.Configuration;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigMapEnricherTest {

    @Mocked
    private MavenEnricherContext context;

    @Before
    public void setupExpectations() {
        new Expectations() {
            {{
                context.getProjectDirectory();
                result = Paths.get("").toFile();
            }}
        };
    }

    @Test
    public void should_materialize_file_content_from_annotation() throws Exception {
        final ConfigMap baseConfigMap = createAnnotationConfigMap("test-application.properties", "src/test/resources/test-application.properties");
        final KubernetesListBuilder builder = new KubernetesListBuilder()
            .addToConfigMapItems(baseConfigMap);
        new ConfigMapEnricher(context).create(PlatformMode.kubernetes, builder);

        final ConfigMap configMap = (ConfigMap) builder.buildFirstItem();

        final Map<String, String> data = configMap.getData();
        assertThat(data)
            .containsEntry("test-application.properties", readFileContentsAsString("src/test/resources/test-application.properties"));

        final Map<String, String> annotations = configMap.getMetadata().getAnnotations();
        assertThat(annotations)
            .isEmpty();
    }

    @Test
    public void should_materialize_binary_file_content_from_annotation() throws Exception {
        final ConfigMap baseConfigMap = createAnnotationConfigMap("test.bin", "src/test/resources/test.bin");
        final KubernetesListBuilder builder = new KubernetesListBuilder()
            .addToConfigMapItems(baseConfigMap);
        new ConfigMapEnricher(context).create(PlatformMode.kubernetes, builder);

        final ConfigMap configMap = (ConfigMap) builder.buildFirstItem();

        final Map<String, String> data = configMap.getData();
        assertThat(data)
            .isEmpty();

        final Map<String, String> binaryData = configMap.getBinaryData();
        assertThat(binaryData)
            .containsEntry("test.bin", "wA==");

        final Map<String, String> annotations = configMap.getMetadata().getAnnotations();
        assertThat(annotations)
            .isEmpty();
    }

    @Test
    public void should_materialize_file_content_from_xml() throws Exception {
        final io.fabric8.maven.core.config.ConfigMap baseConfigMap = createXmlConfigMap("src/test/resources/test-application.properties");
        final ResourceConfig config = new ResourceConfig.Builder()
            .withConfigMap(baseConfigMap)
            .build();
        new Expectations() {{
            context.getConfiguration();
            result = new Configuration.Builder().resource(config).build();
        }};

        final KubernetesListBuilder builder = new KubernetesListBuilder();
        new ConfigMapEnricher(context).create(PlatformMode.kubernetes, builder);

        final ConfigMap configMap = (ConfigMap) builder.buildFirstItem();

        final Map<String, String> data = configMap.getData();
        assertThat(data)
            .containsEntry("test-application.properties", readFileContentsAsString("src/test/resources/test-application.properties"));
    }

    @Test
    public void should_materialize_binary_file_content_from_xml() throws Exception {
        final io.fabric8.maven.core.config.ConfigMap baseConfigMap = createXmlConfigMap("src/test/resources/test.bin");
        final ResourceConfig config = new ResourceConfig.Builder()
            .withConfigMap(baseConfigMap)
            .build();
        new Expectations() {{
            context.getConfiguration();
            result = new Configuration.Builder().resource(config).build();
        }};

        final KubernetesListBuilder builder = new KubernetesListBuilder();
        new ConfigMapEnricher(context).create(PlatformMode.kubernetes, builder);

        final ConfigMap configMap = (ConfigMap) builder.buildFirstItem();

        final Map<String, String> data = configMap.getData();
        assertThat(data)
            .isEmpty();

        final Map<String, String> binaryData = configMap.getBinaryData();
        assertThat(binaryData)
            .containsEntry("test.bin", "wA==");
    }

    private io.fabric8.maven.core.config.ConfigMap createXmlConfigMap(final String file) {
        final ConfigMapEntry configMapEntry = new ConfigMapEntry();
        configMapEntry.setFile(file);
        final io.fabric8.maven.core.config.ConfigMap configMap = new io.fabric8.maven.core.config.ConfigMap();
        configMap.addEntry(configMapEntry);
        return configMap;
    }

    private ConfigMap createAnnotationConfigMap(final String key, final String file) {
        ObjectMetaBuilder metaBuilder = new ObjectMetaBuilder()
            .withNamespace("default");

        Map<String, String> annotations = new HashMap<>();
        annotations.put(ConfigMapEnricher.PREFIX_ANNOTATION + key, file);
        metaBuilder = metaBuilder.withAnnotations(annotations);

        Map<String, String> data = new HashMap<>();
        return new ConfigMapBuilder()
            .withData(data)
            .withMetadata(metaBuilder.build())
            .build();
    }

    private String readFileContentsAsString(final String filePath) throws URISyntaxException, IOException {
        return new String(readFileContentAsBytes(filePath));
    }

    private byte[] readFileContentAsBytes(final String filePath) throws IOException, URISyntaxException {
        return Files.readAllBytes(Paths.get(filePath));
    }
}
