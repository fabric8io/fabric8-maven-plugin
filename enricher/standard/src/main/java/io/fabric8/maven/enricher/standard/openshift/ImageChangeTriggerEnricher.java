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
package io.fabric8.maven.enricher.standard.openshift;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import io.fabric8.openshift.api.model.DeploymentConfigSpecBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ImageChangeTriggerEnricher extends BaseEnricher {
    static final String ENRICHER_NAME = "fmp-openshift-imageChangeTrigger";
    private Boolean enableAutomaticTrigger;
    private Boolean enableImageChangeTrigger;


    public ImageChangeTriggerEnricher(MavenEnricherContext context) {
        super(context, ENRICHER_NAME);
        this.enableAutomaticTrigger = isAutomaticTriggerEnabled(context, true);
        this.enableImageChangeTrigger = getImageChangeTriggerFlag(true);
    }

    @Override
    public void create(PlatformMode platformMode, KubernetesListBuilder builder) {
        if(platformMode.equals(PlatformMode.kubernetes))
            return;

        builder.accept(new TypedVisitor<DeploymentConfigSpecBuilder>() {
                @Override
                public void visit(DeploymentConfigSpecBuilder builder) {
                    Map<String, String> containerToImageMap = new HashMap<>();
                    PodTemplateSpec template = builder.buildTemplate();
                    if (template != null) {
                        PodSpec podSpec = template.getSpec();
                        Objects.requireNonNull(podSpec, "No PodSpec for PodTemplate:" + template);
                        List<Container> containers = podSpec.getContainers();
                        containerToImageMap = containers.stream().collect(Collectors.toMap(Container::getName, Container::getImage));
                    }
                    // add a new image change trigger for the build stream
                    if (containerToImageMap.size() != 0) {
                        if(enableImageChangeTrigger && isOpenShiftMode()) {
                            for (Map.Entry<String, String> entry : containerToImageMap.entrySet()) {
                                String containerName = entry.getKey();

                                if(!getFabric8GeneratedContainers().contains(containerName))
                                    continue;

                                ImageName image = new ImageName(entry.getValue());
                                String tag = image.getTag() != null ? image.getTag() : "latest";
                                builder.addNewTrigger()
                                        .withType("ImageChange")
                                        .withNewImageChangeParams()
                                        .withAutomatic(enableAutomaticTrigger)
                                        .withNewFrom()
                                        .withKind("ImageStreamTag")
                                        .withName(image.getSimpleName() + ":" + tag)
                                        .withNamespace(image.getUser())
                                        .endFrom()
                                        .withContainerNames(containerName)
                                        .endImageChangeParams()
                                        .endTrigger();
                            }
                        }
                    }
                }
            });
    }
}
