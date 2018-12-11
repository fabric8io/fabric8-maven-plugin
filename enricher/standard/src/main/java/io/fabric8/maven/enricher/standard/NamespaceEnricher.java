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
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.kubernetes.KubernetesHelper;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import io.fabric8.openshift.api.model.ImageChangeTriggerBuilder;
import org.apache.commons.lang3.StringUtils;

public class NamespaceEnricher extends BaseEnricher {
    public NamespaceEnricher(MavenEnricherContext buildContext) {
        super(buildContext, "fmp-namespace");
    }

    private enum Config implements Configs.Key {
        namespace;
        public String def() { return d; } protected String d;
    }

    @Override
    public void addMissingResources(KubernetesListBuilder builder) {
        final String namespace = getNamespace();

        builder.accept(new TypedVisitor<ContainerBuilder>() {
            @Override
            public void visit(ContainerBuilder containerBuilder) {
                containerBuilder.withImage(KubernetesHelper.getImageNameWithNamespace(containerBuilder.getImage(), namespace));
            }
        });

        builder.accept(new TypedVisitor<ImageChangeTriggerBuilder>() {
            @Override
            public void visit(ImageChangeTriggerBuilder imageChangeTriggerBuilder) {
                imageChangeTriggerBuilder.editOrNewFrom().withNamespace(namespace).endFrom();
            }
        });

    }

    // Get names space in the order:
    // - plugin configuration
    // - default name space from the kubernetes helper
    // - "default"
    private String getNamespace() {
        String namespace = getConfig(Config.namespace);
        if (StringUtils.isNotBlank(namespace)){
            return namespace;
        }

        namespace = getContext().getConfiguration().getProperty("fabric8.namespace");
        if (StringUtils.isNotBlank(namespace)){
            return namespace;
        }

        namespace = System.getProperty("fabric8.namespace");
        if (StringUtils.isNotBlank(namespace)){
            return namespace;
        }

        return KubernetesHelper.getDefaultNamespace();
    }
}
