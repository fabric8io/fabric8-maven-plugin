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

import io.fabric8.kubernetes.api.builder.BaseFluent;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.enricher.api.Enricher;
import io.fabric8.maven.enricher.api.Kind;
import io.fabric8.maven.enricher.api.MavenBuildContext;
import io.fabric8.maven.plugin.util.PluginServiceFactory;


/**
 * @author roland
 * @since 08/04/16
 */
public class EnricherManager {

    // List of enrichers used for customizing the generated deployment descriptors
    private List<Enricher> enrichers;

    // List of visitors used to enrich with labels
    private List<? extends LabelEnricherVisitor<?>> labelEnricherVisitors;
    private final List<? extends SelectorVisitor<?>> selectorVisitors;

    public EnricherManager(MavenBuildContext buildContext) {
        enrichers = PluginServiceFactory.createServiceObjects(buildContext,
                                                              "META-INF/fabric8-enricher-default", "META-INF/fabric8-enricher");
        Collections.reverse(enrichers);

        labelEnricherVisitors = Arrays.asList(
            new LabelEnricherVisitor.ReplicaSet(this),
            new LabelEnricherVisitor.ReplicationController(this),
            new LabelEnricherVisitor.Service(this),
            new LabelEnricherVisitor.PodTemplate(this));

        selectorVisitors = Arrays.asList(
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
        visit(builder, labelEnricherVisitors);
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

    public Map<String, String> extractLabels(Kind kind) {
        Map <String, String> ret = new HashMap<>();
        for (Enricher enricher : enrichers) {
            putAllIfNotNull(ret, enricher.getLabels(kind));
        }
        return ret;
    }

    Map<String, String> extractSelector(Kind kind) {
        Map <String, String> ret = new HashMap<>();
        for (Enricher enricher : enrichers) {
            putAllIfNotNull(ret, enricher.getSelector(kind));
        }
        return ret;
    }

    private void putAllIfNotNull(Map<String, String> ret, Map<String, String> labels) {
        if (labels != null) {
            ret.putAll(labels);
        }
    }

    private void visit(KubernetesListBuilder builder, List<? extends LabelEnricherVisitor<?>> visitors) {
        for (LabelEnricherVisitor visitor : visitors) {
            builder.accept(visitor);
        }
    }
}
