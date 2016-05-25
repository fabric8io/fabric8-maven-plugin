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

import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.builder.Visitor;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetSpec;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.utils.Strings;

import static io.fabric8.maven.core.handler.Containers.getKubernetesContainerName;

/**
 * @author roland
 * @since 25/05/16
 */
public class DefaultImageEnricher extends BaseEnricher {

    public DefaultImageEnricher(EnricherContext buildContext) {
        super(buildContext);
    }

    @Override
    public String getName() {
        return "default.image";
    }

    @Override
    public void enrich(KubernetesListBuilder builder) {

        builder.accept(new Visitor<ReplicationControllerBuilder>() {
            @Override
            public void visit(ReplicationControllerBuilder item) {
                getOrCreateContainerList(item);
            }
        });

        builder.accept(new Visitor<ReplicaSetBuilder>() {
            @Override
            public void visit(ReplicaSetBuilder item) {
                getOrCreateContainerList(item);
            }
        });
    }


    private List<Container> getOrCreateContainerList(ReplicaSetBuilder rs) {
        ReplicaSetSpec spec = rs.getSpec();
        if (spec == null) {
            spec = new ReplicaSetSpec();
            rs.withSpec(spec);
        }
        PodTemplateSpec template = spec.getTemplate();
        if (template == null) {
            template = new PodTemplateSpec();
            spec.setTemplate(template);
        }
        return getOrCreateContainerList(template);
    }

    private List<Container> getOrCreateContainerList(ReplicationControllerBuilder rc) {
        ReplicationControllerSpec spec = rc.getSpec();
        if (spec == null) {
            spec = new ReplicationControllerSpec();
            rc.withSpec(spec);
        }
        PodTemplateSpec template = spec.getTemplate();
        if (template == null) {
            template = new PodTemplateSpec();
            spec.setTemplate(template);
        }
        return getOrCreateContainerList(template);
    }

    private List<Container> getOrCreateContainerList(PodTemplateSpec template) {
        PodSpec podSpec = template.getSpec();
        if (podSpec == null) {
            podSpec = new PodSpec();
            template.setSpec(podSpec);
        }
        List<Container> containers = podSpec.getContainers();
        if (containers == null) {
            containers = new ArrayList<Container>();
            podSpec.setContainers(containers);
        }
        defaultContainerImages(getConfig().get("pullPolicy", "IfNotPresent"), containers);
        podSpec.setContainers(containers);
        return containers;
    }

    private void defaultContainerImages(String imagePullPolicy, List<Container> containers) {
        int idx = 0;
        List<ImageConfiguration> images = getImages();
        if (images.isEmpty()) {
            log.warn("No resolved images!");
        }
        for (ImageConfiguration imageConfiguration : images) {
            Container container;
            if (idx < containers.size()) {
                container = containers.get(idx);
            } else {
                container = new Container();
                containers.add(container);
            }
            if (Strings.isNullOrBlank(container.getImagePullPolicy())) {
                container.setImagePullPolicy(imagePullPolicy);
            }
            if (Strings.isNullOrBlank(container.getImage())) {
                container.setImage(imageConfiguration.getName());
            }
            if (Strings.isNullOrBlank(container.getName())) {
                container.setName(getKubernetesContainerName(getProject(), imageConfiguration));
            }
            idx++;
        }
    }

}
