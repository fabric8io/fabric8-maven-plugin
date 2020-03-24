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

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.core.config.ConfigMapEntry;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.singletonMap;

public class ConfigMapEnricher extends BaseEnricher {

    protected static final String PREFIX_ANNOTATION = "maven.fabric8.io/cm/";

    public ConfigMapEnricher(MavenEnricherContext enricherContext) {
        super(enricherContext, "fmp-configmap-file");
    }

    @Override
    public void create(PlatformMode platformMode, KubernetesListBuilder builder) {
        addAnnotations(builder);
        addConfigMapFromXmlConfigurations(builder);
    }

    private void addAnnotations(KubernetesListBuilder builder) {
        builder.accept(new TypedVisitor<ConfigMapBuilder>() {

            @Override
            public void visit(ConfigMapBuilder element) {
                final Map<String, String> annotations = element.buildMetadata().getAnnotations();
                if (annotations != null) {
                    try {
                        addConfigMapFromAnnotations(annotations, element);
                    } catch (IOException e) {
                        throw new IllegalArgumentException(e);
                    }
                }
            }
        });
    }

    private void addConfigMapFromAnnotations(final Map<String, String> annotations, final ConfigMapBuilder configMapBuilder) throws IOException {
        final Set<Map.Entry<String, String>> entries = annotations.entrySet();
        for (Iterator<Map.Entry<String, String>> it = entries.iterator(); it.hasNext(); ) {
            Map.Entry<String, String> entry = it.next();
            final String key = entry.getKey();

            if (key.startsWith(PREFIX_ANNOTATION)) {
                addConfigMapEntryFromFile(configMapBuilder, getOutput(key), getContext().resolvePath(entry.getValue()));
                it.remove();
            }
        }
    }

    private void addConfigMapEntryFromFile(final ConfigMapBuilder configMapBuilder, final String key, Path path) throws IOException {
        final byte[] bytes = Files.readAllBytes(path);
        try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes));
            final String value = new String(bytes);
            configMapBuilder.addToData(singletonMap(key, value));
        } catch (CharacterCodingException e) {
            final String value = Base64.getEncoder().encodeToString(bytes);
            configMapBuilder.addToBinaryData(singletonMap(key, value));
        }
    }

    private String getOutput(String key) {
        return key.substring(PREFIX_ANNOTATION.length());
    }

    private void addConfigMapFromXmlConfigurations(KubernetesListBuilder builder) {
        io.fabric8.maven.core.config.ConfigMap configMap = getConfigMapFromXmlConfiguration();
        try {
            if (configMap == null) {
                return;
            }
            String configMapName = configMap.getName() == null || configMap.getName().trim().isEmpty() ? "xmlconfig" : configMap.getName().trim();
            if (checkIfItemExists(builder, configMapName)) {
                return;
            }

            ConfigMapBuilder configMapBuilder = new ConfigMapBuilder();
            configMapBuilder.withNewMetadata().withName(configMapName).endMetadata();

            for (ConfigMapEntry configMapEntry : configMap.getEntries()) {
                String name = configMapEntry.getName();
                final String value = configMapEntry.getValue();
                if (name != null && value != null) {
                    configMapBuilder.addToData(name, value);
                } else {
                    final String file = configMapEntry.getFile();
                    if (file != null) {
                        Path path = getContext().resolvePath(file);
                        if (name == null) {
                            name = path.getFileName().toString();
                        }
                        addConfigMapEntryFromFile(configMapBuilder, name, path);
                    }
                }
            }

            if (configMapBuilder.getData() != null && !configMapBuilder.getData().isEmpty() || !configMapBuilder.getBinaryData().isEmpty()) {
                builder.addToConfigMapItems(configMapBuilder.build());
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private boolean checkIfItemExists(KubernetesListBuilder builder, String name) {
        return builder.buildItems().stream().filter(item -> item.getKind().equals("ConfigMap")).anyMatch(item -> item.getMetadata().getName().equals(name));
    }

    private io.fabric8.maven.core.config.ConfigMap getConfigMapFromXmlConfiguration() {
        ResourceConfig resourceConfig = getConfiguration().getResource().orElse(null);
        if (resourceConfig != null && resourceConfig.getConfigMap() != null) {
            return resourceConfig.getConfigMap();
        }
        return null;
    }
}
