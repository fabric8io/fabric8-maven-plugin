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

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.maven.core.util.Constants;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;

import java.util.List;
import java.util.Map;

import static io.fabric8.kubernetes.api.KubernetesHelper.getKind;
import static io.fabric8.maven.core.util.Constants.RESOURCE_LOCATION_ANNOTATION;
import static io.fabric8.utils.Lists.notNullList;

/**
 * Removes any build time annotations on resources
 */
public class RemoveBuildAnnotationsEnricher extends BaseEnricher {

    public RemoveBuildAnnotationsEnricher(EnricherContext buildContext) {
        super(buildContext, "fmp-remove-build-annotations");
    }

    @Override
    public void adapt(KubernetesListBuilder builder) {
        List<HasMetadata> items = notNullList(builder.getItems());

        for (HasMetadata item : items) {
            removeBuildAnnotations(item);
        }
    }

    private void removeBuildAnnotations(HasMetadata item) {
        if (item != null) {
            ObjectMeta metadata = item.getMetadata();
            if (metadata != null) {
                Map<String, String> annotations = metadata.getAnnotations();
                if (annotations != null) {
                    annotations.remove(RESOURCE_LOCATION_ANNOTATION);
                }
            }
        }
    }
}
