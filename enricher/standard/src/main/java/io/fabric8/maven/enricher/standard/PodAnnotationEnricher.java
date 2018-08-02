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
package io.fabric8.maven.enricher.standard;

import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpec;
import io.fabric8.maven.core.util.MapUtil;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;

/**
 * Enricher which copies the annotation from a Deployment to the annotations of
 * container Pod spec.
 */
public class PodAnnotationEnricher extends BaseEnricher {
    public PodAnnotationEnricher(EnricherContext buildContext) {
        super(buildContext, "fmp-pod-annotations");
    }

    @Override
    public void adapt(KubernetesListBuilder builder) {
        super.adapt(builder);

        List<HasMetadata> items = builder.getItems();
        for (HasMetadata item : items) {
            if (item instanceof Deployment) {
                Deployment deployment = (Deployment) item;
                ObjectMeta metadata = deployment.getMetadata();
                DeploymentSpec spec = deployment.getSpec();
                if (metadata != null && spec != null) {
                    PodTemplateSpec template = spec.getTemplate();
                    if (template != null) {
                        ObjectMeta templateMetadata = template.getMetadata();
                        if (templateMetadata == null) {
                            templateMetadata = new ObjectMeta();
                            template.setMetadata(templateMetadata);
                        }
                        templateMetadata.setAnnotations(MapUtil.mergeMaps(templateMetadata.getAnnotations(), metadata.getAnnotations()));
                    }
                }
            }
        }
        builder.withItems(items);
    }
}
