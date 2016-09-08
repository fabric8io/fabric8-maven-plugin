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
import io.fabric8.maven.core.config.MetaDataConfig;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.util.ClassUtil;
import io.fabric8.maven.core.util.PluginServiceFactory;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.enricher.api.Enricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;
import io.fabric8.utils.Function;

import static io.fabric8.maven.plugin.enricher.EnricherManager.Extractor.*;


/**
 * @author roland
 * @since 08/04/16
 */
public class EnricherManager {

    // List of enrichers used for customizing the generated deployment descriptors
    private List<Enricher> enrichers;

    // context used by enrichers
    private final ProcessorConfig enricherConfig;

    private Logger log;

    // List of visitors used to enrich with labels
    private final List<? extends MetadataVisitor<?>> metaDataVisitors;
    private final List<? extends SelectorVisitor<?>> selectorVisitors;

    public EnricherManager(EnricherContext enricherContext) {
        PluginServiceFactory<EnricherContext> pluginFactory = new PluginServiceFactory<>(enricherContext);


        if (enricherContext.isUseProjectClasspath()) {
            pluginFactory.addAdditionalClassLoader(ClassUtil.createProjectClassLoader(enricherContext.getProject(), enricherContext.getLog()));
        }

        this.log = enricherContext.getLog();
        this.enricherConfig = enricherContext.getConfig();

        this.enrichers = pluginFactory.createServiceObjects("META-INF/fabric8-enricher-default",
                                                       "META-INF/fabric8/enricher-default",
                                                       "META-INF/fabric8-enricher",
                                                       "META-INF/fabric8/enricher");
        Collections.reverse(enrichers);

        ResourceConfig resources = enricherContext.getResourceConfig();
        if (resources != null) {
            addMetaDataEnricher(enrichers, enricherContext, MetadataEnricher.Type.LABEL, resources.getLabels());
            addMetaDataEnricher(enrichers, enricherContext, MetadataEnricher.Type.ANNOTATION, resources.getAnnotations());
        }

        enrichers = filterEnrichers(enrichers);
        logEnrichers(enrichers);


        metaDataVisitors = Arrays.asList(
            new MetadataVisitor.Deployment(this),
            new MetadataVisitor.ReplicaSet(this),
            new MetadataVisitor.ReplicationController(this),
            new MetadataVisitor.Service(this),
            new MetadataVisitor.PodSpec(this));

        selectorVisitors = Arrays.asList(
            new SelectorVisitor.Deployment(this),
            new SelectorVisitor.ReplicaSet(this),
            new SelectorVisitor.ReplicationController(this),
            new SelectorVisitor.Service(this));
    }

    private void addMetaDataEnricher(List<Enricher> enrichers, EnricherContext ctx, MetadataEnricher.Type type, MetaDataConfig metaData) {
        if (metaData != null) {
            enrichers.add(new MetadataEnricher(ctx, type, metaData));
        }
    }

    private void logEnrichers(List<Enricher> enrichers) {
        log.verbose("Enrichers:");
        for (Enricher enricher : enrichers) {
            log.verbose("- %s", enricher.getName());
        }
    }

    public void createDefaultResources(KubernetesListBuilder builder) {
        // Add default resources
        addDefaultResources(builder);
    }

    public void enrich(KubernetesListBuilder builder) {
        // Enrich labels
        enrichLabels(builder);

        // Add missing selectors
        addMissingSelectors(builder);

        // Final customization step
        adapt(builder);
    }


    /**
     * Enrich the given list with labels.
     *
     * @param builder the build to enrich with labels
     */
    private void enrichLabels(KubernetesListBuilder builder) {
        visit(builder, metaDataVisitors);
    }

    /**
     * Add selector when missing to services and replication controller / replica sets
     *
     * @param builder builder to add selectors to.
     */
    private void addMissingSelectors(KubernetesListBuilder builder) {
        for (SelectorVisitor visitor : selectorVisitors) {
            builder.accept(visitor);
        }
    }

    /**
     * Allow enricher to do customizations on their own at the end of the enrichment
     *
     * @param builder builder to customize
     */
    private void adapt(final KubernetesListBuilder builder) {
        loop(new Function<Enricher, Void>() {
            @Override
            public Void apply(Enricher enricher) {
                enricher.adapt(builder);
                return null;
            }
        });
    }

    /**
     * Allow enricher to add default resource objects
     *
     * @param builder builder to examine for missing resources and used for adding default resources to it
     */
    private void addDefaultResources(final KubernetesListBuilder builder) {
        loop(new Function<Enricher, Void>() {
            @Override
            public Void apply(Enricher enricher) {
                enricher.addMissingResources(builder);
                return null;
            }
        });
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


    private List<Enricher> filterEnrichers(List<Enricher> enrichers) {
        List<Enricher> ret = new ArrayList<>();
        for (Enricher enricher : enricherConfig.order(enrichers, "enricher")) {
            if (enricherConfig.use(enricher.getName())) {
                ret.add(enricher);
            }
        }
        return ret;
    }

    private void loop(Function<Enricher, Void> function) {
        for (Enricher enricher : enrichers) {
            function.apply(enricher);
        }
    }

    private Map<String, String> extract(Extractor extractor, Kind kind) {
        Map <String, String> ret = new HashMap<>();
        for (Enricher enricher : enrichers) {
            putAllIfNotNull(ret, extractor.extract(enricher, kind));
        }
        return ret;
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

    private void visit(KubernetesListBuilder builder, List<? extends MetadataVisitor<?>> visitors) {
        for (MetadataVisitor visitor : visitors) {
            builder.accept(visitor);
        }
    }
}
