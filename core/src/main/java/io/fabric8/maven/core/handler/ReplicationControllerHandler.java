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

import java.util.List;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.docker.config.ImageConfiguration;

/**
 * @author roland
 * @since 08/04/16
 */
public class ReplicationControllerHandler {

    private final PodTemplateHandler podTemplateHandler;

    ReplicationControllerHandler(PodTemplateHandler podTemplateHandler) {
        this.podTemplateHandler = podTemplateHandler;
    }

    public ReplicationController getReplicationController(ResourceConfig config,
                                                          List<ImageConfiguration> images) {
        return new ReplicationControllerBuilder()
            .withMetadata(createRcMetaData(config))
            .withSpec(createRcSpec(config, images))
            .build();
    }

    // ===========================================================
    // TODO: "replica set" config used

    private ObjectMeta createRcMetaData(ResourceConfig config) {
        return new ObjectMetaBuilder()
            .withName(KubernetesHelper.validateKubernetesId(config.getReplicaSetName(), "replication controller name"))
            .withAnnotations(config.getAnnotations().getReplicaSet())
            .build();
    }

    private ReplicationControllerSpec createRcSpec(ResourceConfig config, List<ImageConfiguration> images) {
        return new ReplicationControllerSpecBuilder()
            .withReplicas(config.getReplicas())
            .withTemplate(podTemplateHandler.getPodTemplate(config,images))
            .build();
    }
}
