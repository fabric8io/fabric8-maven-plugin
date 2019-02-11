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
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class ConfigMapEnricher extends BaseEnricher {

    protected static final String PREFIX_ANNOTATION = "maven.fabric8.io/cm/";

    public ConfigMapEnricher(MavenEnricherContext enricherContext) {
        super(enricherContext, "fmp-configmap-file");
    }

    @Override
    public void addMissingResources(PlatformMode platformMode, KubernetesListBuilder builder) {
        addAnnotations(builder);
        addConfigMapFromXmlConfigurations(builder);
    }

    private void addAnnotations(KubernetesListBuilder builder) {
        builder.accept(new TypedVisitor<ConfigMapBuilder>() {

            @Override
            public void visit(ConfigMapBuilder element) {
                final Map<String, String> annotations = element.buildMetadata().getAnnotations();
                try {
                    final Map<String, String> configMapAnnotations = createConfigMapFromAnnotations(annotations);
                    element.addToData(configMapAnnotations);
                } catch (IOException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        });
    }

    private Map<String, String> createConfigMapFromAnnotations(final Map<String, String> annotations) throws IOException {
        final Set<Map.Entry<String, String>> entries = annotations.entrySet();
        final Map<String, String> configMapFileLocations = new HashMap<>();

        for(Iterator<Map.Entry<String, String>> it = entries.iterator(); it.hasNext(); ) {
            Map.Entry<String, String> entry = it.next();
            final String key = entry.getKey();

            if(key.startsWith(PREFIX_ANNOTATION)) {
                configMapFileLocations.put(getOutput(key), readContent(entry.getValue()));
                it.remove();
            }
        }

        return configMapFileLocations;
    }

    private String readContent(String location) throws IOException {
        return new String(Files.readAllBytes(Paths.get(location)));
    }

    private String getOutput(String key) {
        return key.substring(PREFIX_ANNOTATION.length());
    }

    private void addConfigMapFromXmlConfigurations(KubernetesListBuilder builder) {
        io.fabric8.maven.core.config.ConfigMap configMap = getConfigMapFromXmlConfiguration();
        final Map<String, String> configMapFromConfiguration;
        try {
            configMapFromConfiguration = createConfigMapFromConfiguration(configMap);
            if(!configMapFromConfiguration.isEmpty()) {
                ConfigMapBuilder element = new ConfigMapBuilder();
                element.withNewMetadata().withName("xmlconfig").endMetadata();
                element.addToData(configMapFromConfiguration);

                builder.addToConfigMapItems(element.build());
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private io.fabric8.maven.core.config.ConfigMap getConfigMapFromXmlConfiguration() {
        ResourceConfig resourceConfig = getConfiguration().getResource().orElse(null);
        if(resourceConfig != null && resourceConfig.getConfigMap() != null) {
            return resourceConfig.getConfigMap();
        }
        return null;
    }

    private Map<String, String> createConfigMapFromConfiguration(io.fabric8.maven.core.config.ConfigMap configMap) throws IOException {
        final Map<String, String> configMapData = new HashMap<>();

        if (configMap != null) {
            for (ConfigMapEntry configMapEntry : configMap.getEntries()) {
                String name = configMapEntry.getName();
                final String value = configMapEntry.getValue();
                if (name != null && value != null) {
                    configMapData.put(name, value);
                } else {
                    final String file = configMapEntry.getFile();
                    if (file != null) {
                        if (name == null) {
                            name = Paths.get(file).getFileName().toString();
                        }
                        configMapData.put(name, readContent(file));
                    }
                }
            }
        }
        return configMapData;
    }

}
