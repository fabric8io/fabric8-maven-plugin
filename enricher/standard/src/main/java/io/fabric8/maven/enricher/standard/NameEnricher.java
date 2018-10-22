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

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder;
import io.fabric8.kubernetes.api.model.ReplicationControllerFluent;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentFluent;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetBuilder;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetFluent;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import org.apache.commons.lang3.StringUtils;

/**
 * Enricher for adding a "name" to the metadata to various objects we create.
 * The name is only added if not already set.
 *
 * The name is added to the following objects:
 *
 * @author roland
 * @since 25/05/16
 */
public class NameEnricher extends BaseEnricher {

    public NameEnricher(MavenEnricherContext buildContext) {
        super(buildContext, "fmp-name");
    }

    private enum Config implements Configs.Key {
        name;
        public String def() { return d; } protected String d;
    }

    @Override
    public void addMissingResources(KubernetesListBuilder builder) {
        final String defaultName = getConfig(Config.name, MavenUtil.createDefaultResourceName(getContext().getGav().getArtifactId()));

        builder.accept(new TypedVisitor<HasMetadata>() {
            @Override
            public void visit(HasMetadata resource) {
                ObjectMeta metadata = getOrCreateMetadata(resource);
                if (StringUtils.isBlank(metadata.getName())) {
                    metadata.setName(defaultName);
                }
            }
        });

        // TODO not sure why this is required for Deployment?
        builder.accept(new TypedVisitor<DeploymentBuilder>() {
            @Override
            public void visit(DeploymentBuilder resource) {
                DeploymentFluent.MetadataNested<DeploymentBuilder> metadata = resource.editMetadata();
                if (metadata == null) {
                    resource.withNewMetadata().withName(defaultName).endMetadata();
                } else {
                    if (StringUtils.isBlank(metadata.getName())) {
                        metadata.withName(defaultName).endMetadata();
                    }
                }
            }
        });
        builder.accept(new TypedVisitor<ReplicationControllerBuilder>() {
            @Override
            public void visit(ReplicationControllerBuilder resource) {
                ReplicationControllerFluent.MetadataNested<ReplicationControllerBuilder> metadata = resource.editMetadata();
                if (metadata == null) {
                    resource.withNewMetadata().withName(defaultName).endMetadata();
                } else {
                    if (StringUtils.isBlank(metadata.getName())) {
                        metadata.withName(defaultName).endMetadata();
                    }
                }
            }
        });
        builder.accept(new TypedVisitor<ReplicaSetBuilder>() {
            @Override
            public void visit(ReplicaSetBuilder resource) {
                ReplicaSetFluent.MetadataNested<ReplicaSetBuilder> metadata = resource.editMetadata();
                if (metadata == null) {
                    resource.withNewMetadata().withName(defaultName).endMetadata();
                } else {
                    if (StringUtils.isBlank(metadata.getName())) {
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
