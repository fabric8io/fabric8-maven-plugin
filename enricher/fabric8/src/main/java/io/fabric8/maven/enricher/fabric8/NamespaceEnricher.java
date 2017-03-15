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
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapFluent;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimFluent;
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder;
import io.fabric8.kubernetes.api.model.ReplicationControllerFluent;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.SecretFluent;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceFluent;
import io.fabric8.kubernetes.api.model.extensions.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.extensions.DeploymentFluent;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetFluent;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import io.fabric8.openshift.api.model.DeploymentConfigFluent;
import io.fabric8.utils.Strings;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Enricher to add namespaces to kinds
 */
public class NamespaceEnricher extends BaseEnricher {

    private String namespace;
    private Set<String> ignoreKinds = new HashSet<>(Arrays.asList("Namespace", "Project", "ProjectRequest", "ServiceAccount"));

    public NamespaceEnricher(EnricherContext buildContext) {
        super(buildContext, "f8-namespace");
        this.namespace = getConfig(Config.namespace);
    }

    // Available configuration keys
    private enum Config implements Configs.Key {
        namespace {{
            d = "";
        }};

        protected String d;

        public String def() {
            return d;
        }
    }

    @Override
    public void adapt(KubernetesListBuilder builder) {
        if (Strings.isNullOrBlank(namespace)) {
            return;
        }
        log.info("Defaulting missing namespaces to: " + namespace);

/*
        builder.accept(new TypedVisitor<HasMetadata>() {
            @Override
            public void visit(HasMetadata resource) {
                defaultMissingNamespace(resource);
            }
        });

*/
        builder.accept(new TypedVisitor<ConfigMapBuilder>() {
            @Override
            public void visit(ConfigMapBuilder resource) {
                ConfigMapFluent.MetadataNested<ConfigMapBuilder> metadata = resource.editMetadata();
                if (metadata != null) {
                    if (Strings.isNullOrBlank(metadata.getNamespace())) {
                        metadata.withNamespace(namespace).endMetadata().build();
                    }
                }
            }
        });
        builder.accept(new TypedVisitor<ServiceBuilder>() {
            @Override
            public void visit(ServiceBuilder resource) {
                ServiceFluent.MetadataNested<ServiceBuilder> metadata = resource.editMetadata();
                if (metadata != null) {
                    if (Strings.isNullOrBlank(metadata.getNamespace())) {
                        metadata.withNamespace(namespace).endMetadata().build();
                    }
                }
            }
        });
        builder.accept(new TypedVisitor<SecretBuilder>() {
            @Override
            public void visit(SecretBuilder resource) {
                SecretFluent.MetadataNested<SecretBuilder> metadata = resource.editMetadata();
                if (metadata != null) {
                    if (Strings.isNullOrBlank(metadata.getNamespace())) {
                        metadata.withNamespace(namespace).endMetadata();
                    }
                }
            }
        });
        builder.accept(new TypedVisitor<PersistentVolumeClaimBuilder>() {
            @Override
            public void visit(PersistentVolumeClaimBuilder resource) {
                PersistentVolumeClaimFluent.MetadataNested<PersistentVolumeClaimBuilder> metadata = resource.editMetadata();
                if (metadata != null) {
                    if (Strings.isNullOrBlank(metadata.getNamespace())) {
                        metadata.withNamespace(namespace).endMetadata();
                    }
                }
            }
        });
        builder.accept(new TypedVisitor<DeploymentBuilder>() {
            @Override
            public void visit(DeploymentBuilder resource) {
                DeploymentFluent.MetadataNested<DeploymentBuilder> metadata = resource.editMetadata();
                if (metadata != null) {
                    if (Strings.isNullOrBlank(metadata.getNamespace())) {
                        metadata.withNamespace(namespace).endMetadata();
                    }
                }
            }
        });
        builder.accept(new TypedVisitor<DeploymentConfigBuilder>() {
            @Override
            public void visit(DeploymentConfigBuilder resource) {
                DeploymentConfigFluent.MetadataNested<DeploymentConfigBuilder> metadata = resource.editMetadata();
                if (metadata != null) {
                    if (Strings.isNullOrBlank(metadata.getNamespace())) {
                        metadata.withNamespace(namespace).endMetadata();
                    }
                }
            }
        });
        builder.accept(new TypedVisitor<ReplicationControllerBuilder>() {
            @Override
            public void visit(ReplicationControllerBuilder resource) {
                ReplicationControllerFluent.MetadataNested<ReplicationControllerBuilder> metadata = resource.editMetadata();
                if (metadata != null) {
                    if (Strings.isNullOrBlank(metadata.getNamespace())) {
                        metadata.withNamespace(namespace).endMetadata();
                    }
                }
            }
        });
        builder.accept(new TypedVisitor<ReplicaSetBuilder>() {
            @Override
            public void visit(ReplicaSetBuilder resource) {
                ReplicaSetFluent.MetadataNested<ReplicaSetBuilder> metadata = resource.editMetadata();
                if (metadata != null) {
                    if (Strings.isNullOrBlank(metadata.getNamespace())) {
                        metadata.withNamespace(namespace).endMetadata();
                    }
                }
            }
        });
    }


}
