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
import java.util.List;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.fabric8.config.AnnotationConfiguration;
import io.fabric8.maven.fabric8.config.KubernetesConfiguration;
import io.fabric8.maven.fabric8.enricher.Kind;

/**
 * @author roland
 * @since 08/04/16
 */
public class ReplicationControllerHandler {

    private final PodTemplateHandler podTemplateHandler;
    private LabelHandler labelHandler;

    ReplicationControllerHandler(PodTemplateHandler podTemplateHandler, LabelHandler labelHandler) {
        this.labelHandler = labelHandler;
        this.podTemplateHandler = podTemplateHandler;
    }

    public ReplicationController getReplicationControllers(KubernetesConfiguration config, List<ImageConfiguration> images) throws IOException {
        return new ReplicationControllerBuilder()
            .withMetadata(createRcMetaData(config))
            .withSpec(createRcSpec(config, images))
            .build();
    }

    // ===========================================================

    private ObjectMeta createRcMetaData(KubernetesConfiguration config) {
        return new ObjectMetaBuilder()
            .withName(KubernetesHelper.validateKubernetesId(config.getRcName(), "replication controller name"))
            .withLabels(labelHandler.extractLabels(Kind.REPLICATION_CONTROLLER, config))
            .withAnnotations(config.getAnnotations().getRc())
            .build();
    }

    private ReplicationControllerSpec createRcSpec(KubernetesConfiguration config, List<ImageConfiguration> images) throws IOException {
        return new ReplicationControllerSpecBuilder()
            .withReplicas(config.getReplicas())
            .withSelector(labelHandler.extractLabels(Kind.REPLICATION_CONTROLLER, config))
            .withTemplate(podTemplateHandler.getPodTemplate(config,images))
            .build();
    }

}
