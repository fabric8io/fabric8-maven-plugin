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

package io.fabric8.maven.core.handler;

import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.maven.core.config.ResourceConfiguration;
import io.fabric8.maven.core.support.VolumeType;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.core.config.VolumeConfiguration;

/**
 * @author roland
 * @since 08/04/16
 */
public class PodTemplateHandler {

    private final ContainerHandler containerHandler;

    PodTemplateHandler(ContainerHandler containerHandler) {
        this.containerHandler = containerHandler;
    }

    public PodTemplateSpec getPodTemplate(ResourceConfiguration config, List<ImageConfiguration> images)  {
        return new PodTemplateSpecBuilder()
            .withMetadata(createPodMetaData(config))
            .withSpec(createPodSpec(config, images))
            .build();
    }

    private ObjectMeta createPodMetaData(ResourceConfiguration config) {
        return new ObjectMetaBuilder()
            .withAnnotations(config.getAnnotations().getPod())
            .build();
    }

    private PodSpec createPodSpec(ResourceConfiguration config, List<ImageConfiguration> images) {

        return new PodSpecBuilder()
            .withServiceAccountName(config.getServiceAccount())
            .withContainers(containerHandler.getContainers(config,images))
            .withVolumes(getVolumes(config))
            .build();
    }

    private List<Volume> getVolumes(ResourceConfiguration config) {
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
