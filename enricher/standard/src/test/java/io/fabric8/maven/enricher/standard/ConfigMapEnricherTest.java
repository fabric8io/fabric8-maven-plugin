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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class ConfigMapEnricherTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "src/test/resources/test-application.properties", false },
                { "src/test/resources/test.bin", true }
        });
    }

    @Mocked
    private MavenEnricherContext context;

    private final Path file;
    private final boolean binary;

    public ConfigMapEnricherTest(String file, boolean binary) {
        this.file = Paths.get(file);
        this.binary = binary;
    }

    @Test
    public void should_materialize_file_content_from_annotation() throws Exception {
        new Expectations() {
            {{
                context.resolvePath(file.toString());
                result = file;
            }}
        };

        final ConfigMap baseConfigMap = createAnnotationConfigMap("my-file", file);
        final KubernetesListBuilder builder = new KubernetesListBuilder()
            .addToConfigMapItems(baseConfigMap);
        new ConfigMapEnricher(context).create(PlatformMode.kubernetes, builder);

        final ConfigMap configMap = (ConfigMap) builder.buildFirstItem();

        if (binary) {
            final Map<String, String> binaryData = configMap.getBinaryData();
            assertThat(binaryData)
                    .containsEntry("my-file", Base64.getEncoder().encodeToString(readFileContentAsBytes(file)));
        } else {
            final Map<String, String> data = configMap.getData();
            assertThat(data)
                    .containsEntry("my-file", readFileContentsAsString(file));
        }

        final Map<String, String> annotations = configMap.getMetadata().getAnnotations();
        assertThat(annotations)
            .isEmpty();
    }

    @Test
    public void should_materialize_file_content_from_xml() throws Exception {
        final io.fabric8.maven.core.config.ConfigMap baseConfigMap = createXmlConfigMap(file);
        final ResourceConfig config = new ResourceConfig.Builder()
            .withConfigMap(baseConfigMap)
            .build();
        new Expectations() {{
            context.getConfiguration();
            result = new Configuration.Builder().resource(config).build();

            context.resolvePath(file.toString());
            result = file;
        }};

        final KubernetesListBuilder builder = new KubernetesListBuilder();
        new ConfigMapEnricher(context).create(PlatformMode.kubernetes, builder);

        final ConfigMap configMap = (ConfigMap) builder.buildFirstItem();

        if (binary) {
            final Map<String, String> binaryData = configMap.getBinaryData();
            assertThat(binaryData)
                    .containsEntry(file.getFileName().toString(), Base64.getEncoder().encodeToString(readFileContentAsBytes(file)));
        } else {
            final Map<String, String> data = configMap.getData();
            assertThat(data)
                    .containsEntry(file.getFileName().toString(), readFileContentsAsString(file));
        }
    }

    private io.fabric8.maven.core.config.ConfigMap createXmlConfigMap(final Path file) {
        final ConfigMapEntry configMapEntry = new ConfigMapEntry();
        configMapEntry.setFile(file.toString());
        final io.fabric8.maven.core.config.ConfigMap configMap = new io.fabric8.maven.core.config.ConfigMap();
        configMap.addEntry(configMapEntry);
        return configMap;
    }

    private ConfigMap createAnnotationConfigMap(final String key, final Path file) {
        ObjectMetaBuilder metaBuilder = new ObjectMetaBuilder()
            .withNamespace("default");

        Map<String, String> annotations = new HashMap<>();
        annotations.put(ConfigMapEnricher.PREFIX_ANNOTATION + key, file.toString());
        metaBuilder = metaBuilder.withAnnotations(annotations);

        Map<String, String> data = new HashMap<>();
        return new ConfigMapBuilder()
            .withData(data)
            .withMetadata(metaBuilder.build())
            .build();
    }

    private String readFileContentsAsString(final Path filePath) throws IOException {
        return new String(readFileContentAsBytes(filePath));
    }

    private byte[] readFileContentAsBytes(final Path path) throws IOException {
        return Files.readAllBytes(path);
    }
}
