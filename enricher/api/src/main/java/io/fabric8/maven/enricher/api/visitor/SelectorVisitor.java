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
package io.fabric8.maven.enricher.api.visitor;

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.DaemonSetSpecBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetSpecBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpecBuilder;
import io.fabric8.kubernetes.api.model.batch.JobSpecBuilder;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.util.MapUtil;
import io.fabric8.maven.enricher.api.Enricher;
import io.fabric8.maven.enricher.api.Kind;
import io.fabric8.openshift.api.model.DeploymentConfigSpecBuilder;

import static io.fabric8.maven.enricher.api.util.ExtractorUtil.extractSelector;

public abstract class SelectorVisitor<T> extends TypedVisitor<T> {

    List<Enricher> enrichers;

    SelectorVisitor(List<Enricher> enrichers) {
        this.enrichers = enrichers;
    }

    private static ThreadLocal<ProcessorConfig> configHolder = new ThreadLocal<>();

    public static void setProcessorConfig(ProcessorConfig config) {
        configHolder.set(config);
    }

    public static void clearProcessorConfig() {
        configHolder.set(null);
    }

    protected static ProcessorConfig getConfig() {
        ProcessorConfig ret = configHolder.get();
        if (ret == null) {
            throw new IllegalArgumentException("Internal: No ProcessorConfig set");
        }
        return ret;
    }

    // ========================================================================

     public static class ServiceSpecBuilderVisitor extends SelectorVisitor<ServiceSpecBuilder> {

        public ServiceSpecBuilderVisitor(List<Enricher> enrichers) {
            super(enrichers);
        }

        @Override
        public void visit(ServiceSpecBuilder item) {
            MapUtil.mergeIfAbsent(item.getSelector(), extractSelector(getConfig(), Kind.SERVICE, enrichers));
        }
    }

    public static class ReplicationControllerSpecBuilderVisitor extends SelectorVisitor<ReplicationControllerSpecBuilder> {
        public ReplicationControllerSpecBuilderVisitor(List<Enricher> enrichers) {
            super(enrichers);
        }

        @Override
        public void visit(ReplicationControllerSpecBuilder item) {
            MapUtil.mergeIfAbsent(item.getSelector(), extractSelector(getConfig(), Kind.REPLICATION_CONTROLLER, enrichers));
        }
    }

    // ============================================================================

    public static class DeploymentSpecBuilderVisitor extends SelectorVisitor<DeploymentSpecBuilder> {

        public DeploymentSpecBuilderVisitor(List<Enricher> enrichers) {
            super(enrichers);
        }

        @Override
        public void visit(DeploymentSpecBuilder item) {
            Map<String, String> selectorMatchLabels = extractSelector(getConfig(), Kind.DEPLOYMENT, enrichers);
            if(!selectorMatchLabels.isEmpty()) {
                LabelSelector selector = item.buildSelector();
                if (selector == null) {
                    item.withNewSelector().addToMatchLabels(selectorMatchLabels).endSelector();
                } else {
                    MapUtil.mergeIfAbsent(selector.getMatchLabels(), selectorMatchLabels);
                }
            }
        }
    }

    public static class DeploymentConfigSpecBuilderVisitor extends SelectorVisitor<DeploymentConfigSpecBuilder> {

        public DeploymentConfigSpecBuilderVisitor(List<Enricher> enrichers) {
            super(enrichers);
        }

        @Override
        public void visit(DeploymentConfigSpecBuilder item) {
            Map<String, String> selectorMatchLabels = extractSelector(getConfig(), Kind.DEPLOYMENT, enrichers);
            if(!selectorMatchLabels.isEmpty()) {
                Map<String, String> selector = item.getSelector();
                if (selector == null) {
                    item.withSelector(selectorMatchLabels);
                } else {
                    MapUtil.mergeIfAbsent(selector, selectorMatchLabels);
                }
            }
        }
    }

    public static class StatefulSetSpecBuilderVisitor extends SelectorVisitor<StatefulSetSpecBuilder> {

        public StatefulSetSpecBuilderVisitor(List<Enricher> enrichers) {
            super(enrichers);
        }

        @Override
        public void visit(StatefulSetSpecBuilder item) {
            Map<String, String> selectorMatchLabels = extractSelector(getConfig(), Kind.STATEFUL_SET, enrichers);
            LabelSelector selector = item.buildSelector();
            if (selector == null) {
                item.withNewSelector().addToMatchLabels(selectorMatchLabels).endSelector();
            } else {
                MapUtil.mergeIfAbsent(selector.getMatchLabels(), selectorMatchLabels);
            }
        }
    }

    public static class DaemonSetSpecBuilderVisitor extends SelectorVisitor<DaemonSetSpecBuilder> {

        public DaemonSetSpecBuilderVisitor(List<Enricher> enrichers) {
            super(enrichers);
        }

        @Override
        public void visit(DaemonSetSpecBuilder item) {
            Map<String, String> selectorMatchLabels = extractSelector(getConfig(), Kind.DAEMON_SET, enrichers);
            final LabelSelector selector = item.buildSelector();
            if (selector == null) {
                item.withNewSelector().addToMatchLabels(selectorMatchLabels).endSelector();
            } else {
                MapUtil.mergeIfAbsent(selector.getMatchLabels(), selectorMatchLabels);
            }
        }
    }

    public static class JobSpecBuilderVisitor extends SelectorVisitor<JobSpecBuilder> {
        public JobSpecBuilderVisitor(List<Enricher> enrichers) {
            super(enrichers);
        }

        @Override
        public void visit(JobSpecBuilder item) {
            Map<String, String> selectorMatchLabels = extractSelector(getConfig(), Kind.JOB, enrichers);
            final LabelSelector selector = item.buildSelector();
            if (selector != null) {
                MapUtil.mergeIfAbsent(selector.getMatchLabels(), selectorMatchLabels);
            }
        }
    }

    public static class ReplicaSetSpecBuilderVisitor extends SelectorVisitor<ReplicaSetSpecBuilder> {

        public ReplicaSetSpecBuilderVisitor(List<Enricher> enrichers) {
            super(enrichers);
        }

        @Override
        public void visit(ReplicaSetSpecBuilder item) {
            Map<String, String> selectorMatchLabels = extractSelector(getConfig(), Kind.REPLICA_SET, enrichers);
            final LabelSelector selector = item.buildSelector();
            if (selector == null) {
                item.withNewSelector().addToMatchLabels(selectorMatchLabels).endSelector();
            } else {
                MapUtil.mergeIfAbsent(selector.getMatchLabels(), selectorMatchLabels);
            }
        }
    }
}

