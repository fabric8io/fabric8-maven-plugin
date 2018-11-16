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
import io.fabric8.maven.enricher.api.Kind;

/**
 * @author roland
 * @since 02/05/16
 */
public abstract class SelectorVisitor<T> extends TypedVisitor<T> {

    final EnricherManager enricherManager;

    SelectorVisitor(EnricherManager enricherManager) {
        this.enricherManager = enricherManager;
    }

    private static ThreadLocal<ProcessorConfig> configHolder = new ThreadLocal<>();

    static void setProcessorConfig(ProcessorConfig config) {
        configHolder.set(config);
    }

    static void clearProcessorConfig() {
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

    static class ServiceSpecBuilderVisitor extends SelectorVisitor<ServiceSpecBuilder> {

        ServiceSpecBuilderVisitor(EnricherManager enricherManager) {
            super(enricherManager);
        }

        @Override
        public void visit(ServiceSpecBuilder item) {
            MapUtil.mergeIfAbsent(item.getSelector(), enricherManager.extractSelector(getConfig(), Kind.SERVICE));
        }
    }

    static class ReplicationControllerSpecBuilderVisitor extends SelectorVisitor<ReplicationControllerSpecBuilder> {
        ReplicationControllerSpecBuilderVisitor(EnricherManager enricherManager) {
            super(enricherManager);
        }

        @Override
        public void visit(ReplicationControllerSpecBuilder item) {
            MapUtil.mergeIfAbsent(item.getSelector(), enricherManager.extractSelector(getConfig(), Kind.REPLICATION_CONTROLLER));
        }
    }

    // ============================================================================

    static class DeploymentSpecBuilderVisitor extends SelectorVisitor<DeploymentSpecBuilder> {

        DeploymentSpecBuilderVisitor(EnricherManager enricherManager) {
            super(enricherManager);
        }

        @Override
        public void visit(DeploymentSpecBuilder item) {
            Map<String, String> selectorMatchLabels = enricherManager.extractSelector(getConfig(), Kind.DEPLOYMENT);
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

    static class StatefulSetSpecBuilderVisitor extends SelectorVisitor<StatefulSetSpecBuilder> {

        StatefulSetSpecBuilderVisitor(EnricherManager enricherManager) {
            super(enricherManager);
        }

        @Override
        public void visit(StatefulSetSpecBuilder item) {
            Map<String, String> selectorMatchLabels = enricherManager.extractSelector(getConfig(), Kind.STATEFUL_SET);
            LabelSelector selector = item.buildSelector();
            if (selector == null) {
                item.withNewSelector().addToMatchLabels(selectorMatchLabels).endSelector();
            } else {
                MapUtil.mergeIfAbsent(selector.getMatchLabels(), selectorMatchLabels);
            }
        }
    }

    static class DaemonSetSpecBuilderVisitor extends SelectorVisitor<DaemonSetSpecBuilder> {

        DaemonSetSpecBuilderVisitor(EnricherManager enricherManager) {
            super(enricherManager);
        }

        @Override
        public void visit(DaemonSetSpecBuilder item) {
            Map<String, String> selectorMatchLabels = enricherManager.extractSelector(getConfig(), Kind.DAEMON_SET);
            final LabelSelector selector = item.buildSelector();
            if (selector == null) {
                item.withNewSelector().addToMatchLabels(selectorMatchLabels).endSelector();
            } else {
                MapUtil.mergeIfAbsent(selector.getMatchLabels(), selectorMatchLabels);
            }
        }
    }

    static class JobSpecBuilderVisitor extends SelectorVisitor<JobSpecBuilder> {
        JobSpecBuilderVisitor(EnricherManager enricherManager) {
            super(enricherManager);
        }

        @Override
        public void visit(JobSpecBuilder item) {
            Map<String, String> selectorMatchLabels = enricherManager.extractSelector(getConfig(), Kind.JOB);
            final LabelSelector selector = item.buildSelector();
            if (selector != null) {
                MapUtil.mergeIfAbsent(selector.getMatchLabels(), selectorMatchLabels);
            }
        }
    }

    static class ReplicaSetSpecBuilderVisitor extends SelectorVisitor<ReplicaSetSpecBuilder> {

        ReplicaSetSpecBuilderVisitor(EnricherManager enricherManager) {
            super(enricherManager);
        }

        @Override
        public void visit(ReplicaSetSpecBuilder item) {
            Map<String, String> selectorMatchLabels = enricherManager.extractSelector(getConfig(), Kind.REPLICA_SET);
            final LabelSelector selector = item.buildSelector();
            if (selector == null) {
                item.withNewSelector().addToMatchLabels(selectorMatchLabels).endSelector();
            } else {
                MapUtil.mergeIfAbsent(selector.getMatchLabels(), selectorMatchLabels);
            }
        }
    }
}
