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

import io.fabric8.maven.core.config.ResourceConfig;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.maven.core.util.Base64Util;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;

public abstract class SecretEnricher extends BaseEnricher {

    public SecretEnricher(MavenEnricherContext buildContext, String name) {
        super(buildContext, name);
    }

    protected String encode(String raw) {
        return Base64Util.encodeToString(raw);
    }

    @Override
    public void addMissingResources(KubernetesListBuilder builder) {

        final ResourceConfig resourceConfig = getConfiguration().getResource().get();

        if (resourceConfig != null) {
            overrideEnvironmentVariables(builder, resourceConfig.getEnv()
                .orElse(new HashMap<>()));
        }

        // update builder
        // use a selector to choose all secret builder in kubernetes list builders.
        // try to find the target annotations
        // use the annotation value to generate data
        // add generated data to this builder
        builder.accept(new TypedVisitor<SecretBuilder>() {
            @Override
            public void visit(SecretBuilder secretBuilder) {
                Map<String, String> annotation = secretBuilder.buildMetadata().getAnnotations();
                if (!annotation.containsKey(getAnnotationKey())) {
                    return;
                }
                String dockerId = annotation.get(getAnnotationKey());
                Map<String, String> data = generateData(dockerId);
                if (data == null) {
                    return;
                }
                // remove the annotation key
                annotation.remove(getAnnotationKey());
                secretBuilder.addToData(data);
            }
        });
    }

    protected abstract String getAnnotationKey();

    protected abstract Map<String, String> generateData(String key);
}
