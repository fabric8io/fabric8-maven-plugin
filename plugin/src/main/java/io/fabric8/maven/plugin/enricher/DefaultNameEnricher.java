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

import io.fabric8.kubernetes.api.builder.Visitor;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder;
import io.fabric8.kubernetes.api.model.ReplicationControllerFluent;
import io.fabric8.kubernetes.api.model.extensions.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.extensions.DeploymentFluent;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetFluent;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.utils.Strings;

/**
 * @author roland
 * @since 25/05/16
 */
public class DefaultNameEnricher extends BaseEnricher {

    public DefaultNameEnricher(EnricherContext buildContext) {
        super(buildContext);
    }

    @Override
    public String getName() {
        return "default.name";
    }

    @Override
    public void enrich(KubernetesListBuilder builder) {
        final String defaultName = getConfig(null, MavenUtil.createDefaultResourceName(getProject()));

        builder.accept(new Visitor<HasMetadata>() {
            @Override
            public void visit(HasMetadata resource) {
                ObjectMeta metadata = getOrCreateMetadata(resource);
                if (Strings.isNullOrBlank(metadata.getName())) {
                    metadata.setName(defaultName);
                }
            }
        });

        // TODO not sure why this is required for Deployment?
        builder.accept(new Visitor<DeploymentBuilder>() {
            @Override
            public void visit(DeploymentBuilder resource) {
                DeploymentFluent.MetadataNested<DeploymentBuilder> metadata = resource.editMetadata();
                if (metadata == null) {
                    resource.withNewMetadata().withName(defaultName).endMetadata();
                } else {
                    if (Strings.isNullOrBlank(metadata.getName())) {
                        metadata.withName(defaultName).endMetadata();
                    }
                }
            }
        });
        builder.accept(new Visitor<ReplicationControllerBuilder>() {
            @Override
            public void visit(ReplicationControllerBuilder resource) {
                ReplicationControllerFluent.MetadataNested<ReplicationControllerBuilder> metadata = resource.editMetadata();
                if (metadata == null) {
                    resource.withNewMetadata().withName(defaultName).endMetadata();
                } else {
                    if (Strings.isNullOrBlank(metadata.getName())) {
                        metadata.withName(defaultName).endMetadata();
                    }
                }
            }
        });
        builder.accept(new Visitor<ReplicaSetBuilder>() {
            @Override
            public void visit(ReplicaSetBuilder resource) {
                ReplicaSetFluent.MetadataNested<ReplicaSetBuilder> metadata = resource.editMetadata();
                if (metadata == null) {
                    resource.withNewMetadata().withName(defaultName).endMetadata();
                } else {
                    if (Strings.isNullOrBlank(metadata.getName())) {
                        metadata.withName(defaultName).endMetadata();
                    }
                }
            }
        });
    }

    private ObjectMeta getOrCreateMetadata(HasMetadata resource) {
        ObjectMeta metadata = resource.getMetadata();
        if (metadata == null) {
            metadata = new ObjectMeta();
            resource.setMetadata(metadata);
        }
        return metadata;
    }
}
