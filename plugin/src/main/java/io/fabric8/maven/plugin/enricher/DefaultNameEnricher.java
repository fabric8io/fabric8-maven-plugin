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

import java.util.List;

import io.fabric8.kubernetes.api.builder.Visitor;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.HasMetadataVisitiableBuilder;
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
        builder.accept(new Visitor<HasMetadata>() {
            @Override
            public void visit(HasMetadata resource) {
                ObjectMeta metadata = getOrCreateMetadata(resource);
                if (Strings.isNullOrBlank(metadata.getName())) {
                    metadata.setName(getConfig().get("", MavenUtil.createDefaultResourceName(getProject())));
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
