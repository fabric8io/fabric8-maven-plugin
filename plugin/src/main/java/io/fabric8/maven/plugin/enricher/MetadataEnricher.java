package io.fabric8.maven.plugin.enricher;
/*
 *
 * Copyright 2016 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.maven.core.config.MetaDataConfig;
import io.fabric8.maven.core.util.MapUtil;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.Enricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author roland
 * @since 05/08/16
 */
public class MetadataEnricher extends BaseEnricher implements Enricher {

    private final Type type;
    private final MetaDataConfig config;

    public MetadataEnricher(EnricherContext context, Type type, MetaDataConfig config) {
        super(context, type == Type.LABEL ? "f8-label" : "f8-annotation");
        this.type = type;
        this.config = config;
    }

    @Override
    public Map<String, String> getLabels(Kind kind) {
        if (type == Type.LABEL) {
            return getConfiguredData(kind);
        } else {
            return null;
        }
    }

    @Override
    public Map<String, String> getAnnotations(Kind kind) {
        if (type == Type.ANNOTATION) {
            return getConfiguredData(kind);
        } else {
            return null;
        }
    }

    @Override
    public void adapt(KubernetesListBuilder builder) {
        final Map<String, String> all = propertiesToMap(config.getAll());
        if (all != null) {
            // Adapt specified all labels to every object in the builder
            builder.accept(new TypedVisitor<ObjectMetaBuilder>() {

                @Override
                public void visit(ObjectMetaBuilder element) {
                    Map<String, String> meta =
                        type == Type.LABEL ?
                            element.getLabels() :
                            element.getAnnotations();
                    MapUtil.mergeIfAbsent(meta, all);
                }
            });
        }
    }

    // ====================================================================

    private Map<String, String> getConfiguredData(Kind kind) {
        if (kind == Kind.SERVICE) {
            return propertiesToMap(config.getService());
        } else if (kind == Kind.DEPLOYMENT || kind == Kind.DEPLOYMENT_CONFIG) {
            return propertiesToMap(config.getDeployment());
        } else if (kind == Kind.REPLICATION_CONTROLLER || kind == Kind.REPLICA_SET) {
            return propertiesToMap(config.getReplicaSet());
        } else if (kind == Kind.POD_SPEC) {
            return propertiesToMap(config.getPod());
        } else {
            return null;
        }
    }

    /**
     * Convert a Properties object into a String/String HashMap
     * @param properties the properties object
     * @return a hashmap of strings mapping the keys to values contained in the original properties list
     */
    private Map<String, String> propertiesToMap(Properties properties) {
        Map<String, String> propertyMap = new HashMap<>();
        if(properties != null) {
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                propertyMap.put(entry.getKey().toString(), entry.getValue().toString());
            }
        }
        return propertyMap;
    }

    public enum Type {
        LABEL,
        ANNOTATION
    }
}
