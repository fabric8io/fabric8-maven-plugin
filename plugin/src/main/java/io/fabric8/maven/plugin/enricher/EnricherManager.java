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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Function;
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

import static io.fabric8.maven.plugin.enricher.EnricherManager.Extractor.ANNOTATION_EXTRACTOR;
import static io.fabric8.maven.plugin.enricher.EnricherManager.Extractor.LABEL_EXTRACTOR;
import static io.fabric8.maven.plugin.enricher.EnricherManager.Extractor.SELECTOR_EXTRACTOR;


/**
 * @author roland
 * @since 08/04/16
 */
public class EnricherManager {

    // Meta data from config
    private final MetaDataConfig labelConfig;
    private final MetaDataConfig annotationConfig;

    // List of enrichers used for customizing the generated deployment descriptors
    private List<Enricher> enrichers;

    // context used by enrichers
    private final ProcessorConfig defaultEnricherConfig;

    private Logger log;

    // List of visitors used to enrich with labels
    private final MetadataVisitor<?>[] metaDataVisitors;
    private final SelectorVisitor<?>[] selectorVisitorCreators;

    public EnricherManager(ResourceConfig resourceConfig, EnricherContext enricherContext) {
        PluginServiceFactory<EnricherContext> pluginFactory = new PluginServiceFactory<>(enricherContext);

        if (enricherContext.isUseProjectClasspath()) {
            pluginFactory.addAdditionalClassLoader(ClassUtil.createProjectClassLoader(enricherContext.getProject(), enricherContext.getLog()));
        }

        this.log = enricherContext.getLog();
        this.defaultEnricherConfig = enricherContext.getConfig();

        this.enrichers = pluginFactory.createServiceObjects("META-INF/fabric8-enricher-default",
                                                            "META-INF/fabric8/enricher-default",
                                                            "META-INF/fabric8-enricher",
                                                            "META-INF/fabric8/enricher");

        if (resourceConfig != null) {
            labelConfig = resourceConfig.getLabels();
            annotationConfig = resourceConfig.getAnnotations();
        } else {
            labelConfig = null;
            annotationConfig = null;
        }

        logEnrichers(filterEnrichers(defaultEnricherConfig, enrichers));

        metaDataVisitors = new MetadataVisitor[] {
            new MetadataVisitor.DeploymentBuilderVisitor(resourceConfig, this),
            new MetadataVisitor.ReplicaSet(resourceConfig, this),
            new MetadataVisitor.ReplicationControllerBuilderVisitor(resourceConfig, this),
            new MetadataVisitor.ServiceBuilderVisitor(resourceConfig, this),
            new MetadataVisitor.PodTemplateSpecBuilderVisitor(resourceConfig, this),
            new MetadataVisitor.DaemonSetBuilderVisitor(resourceConfig, this),
            new MetadataVisitor.StatefulSetBuilderVisitor(resourceConfig, this),
            new MetadataVisitor.JobBuilderVisitor(resourceConfig, this),
        };

        selectorVisitorCreators = new SelectorVisitor[] {
            new SelectorVisitor.DeploymentSpecBuilderVisitor(this),
            new SelectorVisitor.ReplicaSetSpecBuilderVisitor(this),
            new SelectorVisitor.ReplicationControllerSpecBuilderVisitor(this),
            new SelectorVisitor.ServiceSpecBuilderVisitor(this),
            new SelectorVisitor.DaemonSetSpecBuilderVisitor(this),
            new SelectorVisitor.StatefulSetSpecBuilderVisitor(this),
            new SelectorVisitor.JobSpecBuilderVisitor(this)
        };
    }

    public void createDefaultResources(final KubernetesListBuilder builder) {
        createDefaultResources(defaultEnricherConfig, builder);
    }

    public void createDefaultResources(ProcessorConfig enricherConfig, final KubernetesListBuilder builder) {
        // Add default resources
        loop(enricherConfig, new Function<Enricher, Void>() {
            @Override
            public Void apply(Enricher enricher) {
                enricher.addMissingResources(builder);
                return null;
            }
        });
    }

    public void enrich(KubernetesListBuilder builder) {
        enrich(defaultEnricherConfig, builder);
    }

    public void enrich(ProcessorConfig config, KubernetesListBuilder builder) {
        // Enrich labels
        enrichLabels(config, builder);

        // Add missing selectors
        addMissingSelectors(config, builder);

        // Final customization step
        adapt(config, builder);
    }


    // ==================================================================================================


    private void logEnrichers(List<Enricher> enrichers) {
        log.verbose("Enrichers:");
        for (Enricher enricher : enrichers) {
            log.verbose("- %s", enricher.getName());
        }
    }

    /**
     * Enrich the given list with labels.
     *
     * @param builder the build to enrich with labels
     */
    private void enrichLabels(ProcessorConfig config, KubernetesListBuilder builder) {
        visit(config, builder, metaDataVisitors);
    }

    /**
     * Add selector when missing to services and replication controller / replica sets
     *
     * @param config processor config to use
     * @param builder builder to add selectors to.
     */
    private void addMissingSelectors(ProcessorConfig config, KubernetesListBuilder builder) {
        SelectorVisitor.setProcessorConfig(config);
        try {
            for (SelectorVisitor<?> visitor : selectorVisitorCreators) {
                builder.accept(visitor);
            }
        } finally {
            SelectorVisitor.clearProcessorConfig();
        }
    }

    /**
     * Allow enricher to do customizations on their own at the end of the enrichment
     *
     * @param builder builder to customize
     */
    private void adapt(final ProcessorConfig enricherConfig, final KubernetesListBuilder builder) {
        loop(enricherConfig, new Function<Enricher, Void>() {
            @Override
            public Void apply(Enricher enricher) {
                enricher.adapt(builder);
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
    Map<String, String> extractLabels(ProcessorConfig config, Kind kind) {
        return extract(config, LABEL_EXTRACTOR, kind);
    }

    Map<String, String> extractAnnotations(ProcessorConfig config, Kind kind) {
        return extract(config, ANNOTATION_EXTRACTOR, kind);
    }

    Map<String, String> extractSelector(ProcessorConfig config, Kind kind) {
        return extract(config, SELECTOR_EXTRACTOR, kind);
    }


    private List<Enricher> filterEnrichers(ProcessorConfig config, List<Enricher> enrichers) {
        return config.prepareProcessors(enrichers, "enricher");
    }

    private void loop(ProcessorConfig config, Function<Enricher, Void> function) {
        for (Enricher enricher : filterEnrichers(config,enrichers)) {
            function.apply(enricher);
        }
    }

    private Map<String, String> extract(ProcessorConfig config, Extractor extractor, Kind kind) {
        Map <String, String> ret = new HashMap<>();
        for (Enricher enricher : filterEnrichers(config, enrichers)) {
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

    private void visit(ProcessorConfig config, KubernetesListBuilder builder, MetadataVisitor<?>[] visitors) {
        MetadataVisitor.setProcessorConfig(config);
        try {
            for (MetadataVisitor<?> visitor : visitors) {
                builder.accept(visitor);
            }
        } finally {
            MetadataVisitor.clearProcessorConfig();
        }
    }
}
