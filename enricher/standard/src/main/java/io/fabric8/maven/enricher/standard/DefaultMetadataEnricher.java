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

import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.Enricher;
import io.fabric8.maven.enricher.api.Kind;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import java.util.List;
import io.fabric8.maven.enricher.api.visitor.SelectorVisitor;
import io.fabric8.maven.enricher.api.visitor.MetadataVisitor;

public class DefaultMetadataEnricher extends BaseEnricher {

    // List of enrichers used for customizing the generated deployment descriptors
    private List<Enricher> enrichers;

    // List of visitors used to enrich with labels
    private MetadataVisitor<?>[] metaDataVisitors = null;
    private SelectorVisitor<?>[] selectorVisitorCreators = null;

    // context used by enrichers
    private final ProcessorConfig defaultEnricherConfig;

    private final ResourceConfig resourceConfig;

    public DefaultMetadataEnricher(MavenEnricherContext buildContext) {
        super(buildContext, "fmp-metadata");

        this.defaultEnricherConfig = buildContext.getConfiguration().getProcessorConfig().orElse(ProcessorConfig.EMPTY);
        this.resourceConfig = buildContext.getConfiguration().getResource().orElse(null);
    }

    private void init() {

        this.metaDataVisitors = new MetadataVisitor[] {
                new MetadataVisitor.DeploymentBuilderVisitor(resourceConfig, enrichers),
                new MetadataVisitor.DeploymentConfigBuilderVisitor(resourceConfig, enrichers),
                new MetadataVisitor.ReplicaSet(resourceConfig,  enrichers),
                new MetadataVisitor.ReplicationControllerBuilderVisitor(resourceConfig,  enrichers),
                new MetadataVisitor.ServiceBuilderVisitor(resourceConfig,  enrichers),
                new MetadataVisitor.PodTemplateSpecBuilderVisitor(resourceConfig,  enrichers),
                new MetadataVisitor.DaemonSetBuilderVisitor(resourceConfig,  enrichers),
                new MetadataVisitor.StatefulSetBuilderVisitor(resourceConfig,  enrichers),
                new MetadataVisitor.JobBuilderVisitor(resourceConfig,  enrichers),
                new MetadataVisitor.ImageStreamBuilderVisitor(resourceConfig,  enrichers),
                new MetadataVisitor.BuildConfigBuilderVisitor(resourceConfig,  enrichers),
                new MetadataVisitor.BuildBuilderVisitor(resourceConfig,  enrichers),

        };

        this.selectorVisitorCreators = new SelectorVisitor[] {
                new SelectorVisitor.DeploymentSpecBuilderVisitor(enrichers),
                new SelectorVisitor.DeploymentConfigSpecBuilderVisitor(enrichers),
                new SelectorVisitor.ReplicaSetSpecBuilderVisitor(enrichers),
                new SelectorVisitor.ReplicationControllerSpecBuilderVisitor(enrichers),
                new SelectorVisitor.ServiceSpecBuilderVisitor(enrichers),
                new SelectorVisitor.DaemonSetSpecBuilderVisitor(enrichers),
                new SelectorVisitor.StatefulSetSpecBuilderVisitor(enrichers),
                new SelectorVisitor.JobSpecBuilderVisitor(enrichers)
        };
    }

    @Override
    public void addMetadata(PlatformMode platformMode, KubernetesListBuilder builder, List<Enricher> enrichers) {
        this.enrichers = enrichers;

        init();
        // Enrich labels
        enrichLabels(defaultEnricherConfig, builder);

        // Add missing selectors
        addMissingSelectors(defaultEnricherConfig, builder);
    }

    /**
     * Enrich the given list with labels.
     *
     * @param builder the build to enrich with labels
     */
    private void enrichLabels(ProcessorConfig config, KubernetesListBuilder builder) {
        visit(config, builder, metaDataVisitors);
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

}

