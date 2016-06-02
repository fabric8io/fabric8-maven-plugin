/*
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

package io.fabric8.maven.plugin.enricher;

import java.util.*;

import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.core.util.PluginServiceFactory;
import io.fabric8.maven.enricher.api.Enricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;

import static io.fabric8.maven.plugin.enricher.EnricherManager.Extractor.*;


/**
 * @author roland
 * @since 08/04/16
 */
public class EnricherManager {

    // List of enrichers used for customizing the generated deployment descriptors
    private List<Enricher> enrichers;

    // List of visitors used to enrich with labels
    private final List<? extends MetadataEnricherVisitor<?>> metaDataEnricherVisitors;
    private final List<? extends SelectorVisitor<?>> selectorVisitors;

    public EnricherManager(EnricherContext buildContext) {
        PluginServiceFactory<EnricherContext> pluginFactory = new PluginServiceFactory<>(buildContext);

        enrichers = pluginFactory.createServiceObjects("META-INF/fabric8-enricher-default",
                                                       "META-INF/fabric8-enricher");
        Collections.reverse(enrichers);

        metaDataEnricherVisitors = Arrays.asList(
            new MetadataEnricherVisitor.Deployment(this),
            new MetadataEnricherVisitor.ReplicaSet(this),
            new MetadataEnricherVisitor.ReplicationController(this),
            new MetadataEnricherVisitor.Service(this),
            new MetadataEnricherVisitor.PodTemplate(this));

        selectorVisitors = Arrays.asList(
            new SelectorVisitor.Deployment(this),
            new SelectorVisitor.ReplicaSet(this),
            new SelectorVisitor.ReplicationController(this),
            new SelectorVisitor.Service(this));
    }


    /**
     * Enrich the given list with labels.
     *
     * @param builder the build to enrich with labels
     */
    public void enrichLabels(KubernetesListBuilder builder) {
        visit(builder, metaDataEnricherVisitors);
    }

    /**
     * Add selector when missing to services and replication controller / replica sets
     *
     * @param builder builder to add selectors to.
     */
    public void addMissingSelectors(KubernetesListBuilder builder) {
        for (SelectorVisitor visitor : selectorVisitors) {
            builder.accept(visitor);
        }
    }

    /**
     * Allow enricher to do customizations on their own
     *
     * @param builder builder to customize
     */
    public void enrich(KubernetesListBuilder builder) {
        for (Enricher enricher : enrichers) {
            enricher.enrich(builder);
        }
    }

    // =============================================================================================

    /**
     * Get all labels from all enrichers for a certain kind
     *
     * @param kind resource type for which labels should be extracted
     * @return extracted labels
     */
    Map<String, String> extractLabels(Kind kind) {
        return extract(LABEL_EXTRACTOR, kind);
    }

    Map<String, String> extractAnnotations(Kind kind) {
        return extract(ANNOTATION_EXTRACTOR, kind);
    }

    Map<String, String> extractSelector(Kind kind) {
        return extract(SELECTOR_EXTRACTOR, kind);
    }

    private Map<String, String> extract(Extractor extractor, Kind kind) {
        Map <String, String> ret = new HashMap<>();
        for (Enricher enricher : enrichers) {
            putAllIfNotNull(ret, extractor.extract(enricher, kind));
        }
        return ret;
    }

    /**
     * Add programmatically an enricher at the end of the enricher list
     *
     * @param enricher enricher to add
     */
    public void addEnricher(Enricher enricher) {
        enrichers.add(enricher);
    }

    // ========================================================================================================
    // Simple extractors
    enum Extractor {
        LABEL_EXTRACTOR {
            public Map<String, String> extract(Enricher enricher, Kind kind) {
                return enricher.getLabels(kind);
            }
        },
        ANNOTATION_EXTRACTOR {
            public Map<String, String> extract(Enricher enricher, Kind kind) {
                return enricher.getAnnotations(kind);
            }
        },
        SELECTOR_EXTRACTOR {
            public Map<String, String> extract(Enricher enricher, Kind kind) {
                return enricher.getSelector(kind);
            }
        };
        abstract Map<String, String> extract(Enricher enricher, Kind kind);
    }


    private void putAllIfNotNull(Map<String, String> ret, Map<String, String> toPut) {
        if (toPut != null) {
            ret.putAll(toPut);
        }
    }

    private void visit(KubernetesListBuilder builder, List<? extends MetadataEnricherVisitor<?>> visitors) {
        for (MetadataEnricherVisitor visitor : visitors) {
            builder.accept(visitor);
        }
    }
}
