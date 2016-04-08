package io.fabric8.maven.fabric8.handler;
/*
 * 
 * Copyright 2016 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.fabric8.config.KubernetesConfiguration;
import io.fabric8.maven.fabric8.config.VolumeConfiguration;
import io.fabric8.maven.fabric8.enricher.Kind;
import io.fabric8.maven.fabric8.support.VolumeType;

/**
 * @author roland
 * @since 08/04/16
 */
public class PodTemplateHandler {

    private final LabelHandler labelHandler;
    private final ContainerHandler containerHandler;

    PodTemplateHandler(ContainerHandler containerHandler, LabelHandler labelHandler) {
        this.labelHandler = labelHandler;
        this.containerHandler = containerHandler;
    }

    public PodTemplateSpec getPodTemplate(KubernetesConfiguration config, List<ImageConfiguration> images) throws IOException {
        return new PodTemplateSpecBuilder()
            .withMetadata(createPodMetaData(config))
            .withSpec(createPodSpec(config, images))
            .build();
    }

    private ObjectMeta createPodMetaData(KubernetesConfiguration config) {
        return new ObjectMetaBuilder()
            .withLabels(labelHandler.extractLabels(Kind.POD, config))
            .withAnnotations(config.getAnnotations().getPod())
            .build();
    }

    private PodSpec createPodSpec(KubernetesConfiguration config, List<ImageConfiguration> images) throws IOException {

        return new PodSpecBuilder()
            .withServiceAccountName(config.getServiceAccount())
            .withContainers(containerHandler.getContainers(config,images))
            .withVolumes(getVolumes(config))
            .build();
    }

    private List<Volume> getVolumes(KubernetesConfiguration config) {
        List<VolumeConfiguration> volumeConfigs = config.getVolumes();

        List<Volume> ret = new ArrayList<>();
        if (volumeConfigs != null) {
            for (VolumeConfiguration volumeConfig : volumeConfigs) {
                VolumeType type = VolumeType.typeFor(volumeConfig.getType());
                if (type != null) {
                    ret.add(type.fromConfig(volumeConfig));
                }
            }
        }
        return ret;
    }



}
