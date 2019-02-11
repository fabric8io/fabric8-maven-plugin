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
package io.fabric8.maven.plugin.enricher;

import java.util.List;
import java.util.Optional;

import com.google.common.base.Function;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.util.ClassUtil;
import io.fabric8.maven.core.util.PluginServiceFactory;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.enricher.api.Enricher;
import io.fabric8.maven.enricher.api.EnricherContext;

import static io.fabric8.maven.enricher.api.util.Misc.filterEnrichers;

/**
 * @author roland
 * @since 08/04/16
 */
public class EnricherManager {

    // List of enrichers used for customizing the generated deployment descriptors
    private List<Enricher> enrichers;

    // context used by enrichers
    private final ProcessorConfig defaultEnricherConfig;

    private Logger log;

    public EnricherManager(ResourceConfig resourceConfig, EnricherContext enricherContext, Optional<List<String>> extraClasspathElements) {
        PluginServiceFactory<EnricherContext> pluginFactory = new PluginServiceFactory<>(enricherContext);

        extraClasspathElements.ifPresent(
                cpElements -> pluginFactory.addAdditionalClassLoader(ClassUtil.createProjectClassLoader(cpElements, enricherContext.getLog())));

        this.log = enricherContext.getLog();
        this.defaultEnricherConfig = enricherContext.getConfiguration().getProcessorConfig().orElse(ProcessorConfig.EMPTY);

        this.enrichers = pluginFactory.createServiceObjects("META-INF/fabric8-enricher-default",
                "META-INF/fabric8/enricher-default",
                "META-INF/fabric8-enricher",
                "META-INF/fabric8/enricher");

        logEnrichers(filterEnrichers(defaultEnricherConfig, enrichers));

    }

    public void createDefaultResources(PlatformMode platformMode, final KubernetesListBuilder builder) {
        createDefaultResources(platformMode, defaultEnricherConfig, builder);
    }

    public void createDefaultResources(PlatformMode platformMode, ProcessorConfig enricherConfig, final KubernetesListBuilder builder) {
        // Add default resources
        loop(enricherConfig, enricher -> {
            enricher.addMissingResources(platformMode, builder);
            return null;
        });
    }

    public void enrich(PlatformMode platformMode, KubernetesListBuilder builder) {
        enrich(platformMode, defaultEnricherConfig, builder);
    }

    public void enrich(PlatformMode platformMode, ProcessorConfig config, KubernetesListBuilder builder) {
        // Add Metadata labels (
        addMetadata(platformMode, config, builder, enrichers);

        // Final customization step
        adapt(platformMode, config, builder);
    }

    /**
     * Allow enricher to add Metadata to the resources.
     *
     * @param builder builder to customize
     * @param enricherList list of enrichers
     */
    private void addMetadata(PlatformMode platformMode, final ProcessorConfig enricherConfig, final KubernetesListBuilder builder, final List<Enricher> enricherList) {
        loop(enricherConfig, (Enricher enricher) -> {
                enricher.addMetadata(PlatformMode.kubernetes, builder, enricherList);
                return null;
            });
    }

    /**
     * Allow enricher to do customizations on their own at the end of the enrichment
     *
     * @param builder builder to customize
     */
    private void adapt(PlatformMode platformMode, final ProcessorConfig enricherConfig, final KubernetesListBuilder builder) {
        loop(enricherConfig, (Enricher enricher) -> {
                enricher.adapt(platformMode, builder);
                return null;
            });
    }

    private void logEnrichers(List<Enricher> enrichers) {
        log.verbose("Enrichers:");
        for (Enricher enricher : enrichers) {
            log.verbose("- %s", enricher.getName());
        }
    }

    private void loop(ProcessorConfig config, Function<Enricher, Void> function) {
        for (Enricher enricher : filterEnrichers(config, enrichers)) {
            function.apply(enricher);
        }
    }

}