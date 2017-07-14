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

package io.fabric8.maven.enricher.fabric8;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder;
import io.fabric8.kubernetes.api.model.extensions.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetBuilder;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.openshift.api.model.DeploymentConfigBuilder;

/**
 * Enricher for customizing the deployment in fabric8:watch mode.
 *
 * @author nicola
 * @since 16/05/17
 */
public class WatchEnricher extends BaseEnricher {

    public WatchEnricher(EnricherContext buildContext) {
        super(buildContext, "f8-watch");
    }

    @Override
    public void adapt(KubernetesListBuilder builder) {
        if (getContext().isWatchMode()) {
            scaleDownToOnePod(builder);
        }
    }

    private void scaleDownToOnePod(KubernetesListBuilder builder) {

        builder.accept(new TypedVisitor<ReplicaSetBuilder>() {
            @Override
            public void visit(ReplicaSetBuilder b) {
                b.editOrNewSpec().withReplicas(1).endSpec();
            }
        });
        builder.accept(new TypedVisitor<ReplicationControllerBuilder>() {
            @Override
            public void visit(ReplicationControllerBuilder b) {
                b.editOrNewSpec().withReplicas(1).endSpec();
            }
        });
        builder.accept(new TypedVisitor<DeploymentBuilder>() {
            @Override
            public void visit(DeploymentBuilder b) {
                b.editOrNewSpec().withReplicas(1).endSpec();
            }
        });
        builder.accept(new TypedVisitor<DeploymentConfigBuilder>() {
            @Override
            public void visit(DeploymentConfigBuilder b) {
                b.editOrNewSpec().withReplicas(1).endSpec();
            }
        });
        builder.accept(new TypedVisitor<StatefulSetBuilder>() {
            @Override
            public void visit(StatefulSetBuilder b) {
                b.editOrNewSpec().withReplicas(1).endSpec();
            }
        });

    }
}
