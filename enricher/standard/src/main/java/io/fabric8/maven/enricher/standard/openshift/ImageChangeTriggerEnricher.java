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
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import io.fabric8.openshift.api.model.DeploymentConfigSpecBuilder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ImageChangeTriggerEnricher extends BaseEnricher {
    static final String ENRICHER_NAME = "fmp-openshift-imageChangeTrigger";
    private Boolean enableAutomaticTrigger;
    private Boolean enableImageChangeTrigger;
    private Boolean trimImageInContainerSpecFlag;


    private enum Config implements Configs.Key {
        containers {{ d = ""; }};

        public String def() { return d; } protected String d;
    }

    public ImageChangeTriggerEnricher(MavenEnricherContext context) {
        super(context, ENRICHER_NAME);
        this.enableAutomaticTrigger = isAutomaticTriggerEnabled(context, true);
        this.enableImageChangeTrigger = getImageChangeTriggerFlag(true);
        this.trimImageInContainerSpecFlag = getTrimImageInContainerSpecFlag(false);
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
                        for(Container container : containers) {
                            if(container.getName() != null && container.getImage() != null) {
                                containerToImageMap.put(container.getName(), container.getImage());
                            }
                        }
                    }
                    // add a new image change trigger for the build stream
                    if (containerToImageMap.size() != 0) {
                        if(enableImageChangeTrigger && isOpenShiftMode()) {
                            for (Map.Entry<String, String> entry : containerToImageMap.entrySet()) {
                                String containerName = entry.getKey();

                                if(!isImageChangeTriggerNeeded(containerName))
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
                            if(trimImageInContainerSpecFlag) {
                                builder.editTemplate().editSpec().withContainers(trimImagesInContainers(template)).endSpec().endTemplate();
                            }
                        }

                    }
                }
            });
    }

    private Boolean isImageChangeTriggerNeeded(String containerName) {
        String containersFromConfig = Configs.asString(getConfig(Config.containers));
        Boolean enrichAll = enrichAllWithImageChangeTrigger((MavenEnricherContext)enricherContext, false);

        if(enrichAll) {
            return true;
        }

        if(!(getProcessingInstructionViaKey(FABRIC8_GENERATED_CONTAINERS).contains(containerName)  ||
                getProcessingInstructionViaKey(NEED_IMAGECHANGE_TRIGGERS).contains(containerName) ||
                Arrays.asList(containersFromConfig.split(",")).contains(containerName))) {
            return false;
        }
        
        return true;
    }

    private List<Container> trimImagesInContainers(PodTemplateSpec template) {
        List<Container> containers = template.getSpec().getContainers();
        containers.forEach(container -> container.setImage(""));
        return containers;
    }
}
