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

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.ReplicationControllerSpecBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.api.model.extensions.*;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.util.KubernetesResourceUtil;
import io.fabric8.maven.enricher.api.Kind;

import java.util.Map;

/**
 * @author roland
 * @since 02/05/16
 */
public abstract class SelectorVisitor<T> extends TypedVisitor<T> {

    protected final EnricherManager enricherManager;

    public SelectorVisitor(EnricherManager enricherManager) {
        this.enricherManager = enricherManager;
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

    static public class ServiceSpecBuilderVisitor extends SelectorVisitor<ServiceSpecBuilder> {

        public ServiceSpecBuilderVisitor(EnricherManager enricherManager) {
            super(enricherManager);
        }

        @Override
        public void visit(ServiceSpecBuilder item) {
            item.getSelector().putAll(enricherManager.extractSelector(getConfig(), Kind.SERVICE));
        }
    }

    static public class DeploymentSpecBuilderVisitor extends SelectorVisitor<DeploymentSpecBuilder> {

        public DeploymentSpecBuilderVisitor(EnricherManager enricherManager) {
            super(enricherManager);
        }

        @Override
        public void visit(DeploymentSpecBuilder item) {
            Map<String, String> selectorMatchLabels =
                KubernetesResourceUtil.removeVersionSelector(enricherManager.extractSelector(getConfig(), Kind.REPLICATION_CONTROLLER));
            LabelSelector selector = item.getSelector();
            if (selector == null) {
                item.withNewSelector().addToMatchLabels(selectorMatchLabels).endSelector();
            } else {
                selector.getMatchLabels().putAll(selectorMatchLabels);
            }
        }
    }

    static public class PetSetSpecBuilderVisitor extends SelectorVisitor<PetSetSpecBuilder> {

        public PetSetSpecBuilderVisitor(EnricherManager enricherManager) {
            super(enricherManager);
        }

        @Override
        public void visit(PetSetSpecBuilder item) {
            Map<String, String> selectorMatchLabels =
                    KubernetesResourceUtil.removeVersionSelector(enricherManager.extractSelector(getConfig(), Kind.PET_SET));
            final io.fabric8.kubernetes.api.model.LabelSelector selector = item.getSelector();
            if (selector == null) {
                item.withNewSelector().addToMatchLabels(selectorMatchLabels).endSelector();
            } else {
                selector.getMatchLabels().putAll(selectorMatchLabels);
            }
        }
    }

    static public class DaemonSetSpecBuilderVisitor extends SelectorVisitor<DaemonSetSpecBuilder> {

        public DaemonSetSpecBuilderVisitor(EnricherManager enricherManager) {
            super(enricherManager);
        }

        @Override
        public void visit(DaemonSetSpecBuilder item) {
            Map<String, String> selectorMatchLabels =
                    KubernetesResourceUtil.removeVersionSelector(enricherManager.extractSelector(getConfig(), Kind.DAEMON_SET));
            final LabelSelector selector = item.getSelector();
            if (selector == null) {
                item.withNewSelector().addToMatchLabels(selectorMatchLabels).endSelector();
            } else {
                selector.getMatchLabels().putAll(selectorMatchLabels);
            }
        }
    }

    static public class ReplicationControllerSpecBuilderVisitor extends SelectorVisitor<ReplicationControllerSpecBuilder> {
        public ReplicationControllerSpecBuilderVisitor(EnricherManager enricherManager) {
            super(enricherManager);
        }

        @Override
        public void visit(ReplicationControllerSpecBuilder item) {
            item.getSelector().putAll(enricherManager.extractSelector(getConfig(), Kind.REPLICATION_CONTROLLER));
        }
    }

    static public class ReplicaSetSpecBuilderVisitor extends SelectorVisitor<ReplicaSetSpecBuilder> {

        public ReplicaSetSpecBuilderVisitor(EnricherManager enricherManager) {
            super(enricherManager);
        }

        @Override
        public void visit(ReplicaSetSpecBuilder item) {
            item.withSelector(createLabelSelector(enricherManager.extractSelector(getConfig(), Kind.REPLICA_SET)));
        }

        private LabelSelector createLabelSelector(Map<String, String> labelSelector) {
            return new LabelSelectorBuilder().withMatchLabels(labelSelector).build();
        }
    }

}
