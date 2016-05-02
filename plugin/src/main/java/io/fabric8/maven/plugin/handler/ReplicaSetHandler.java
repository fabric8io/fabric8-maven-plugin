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

package io.fabric8.maven.plugin.handler;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.extensions.*;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.plugin.config.KubernetesConfiguration;
import io.fabric8.maven.enricher.api.Kind;

/**
 * @author roland
 * @since 08/04/16
 */
public class ReplicaSetHandler {

    private final PodTemplateHandler podTemplateHandler;

    ReplicaSetHandler(PodTemplateHandler podTemplateHandler) {
        this.podTemplateHandler = podTemplateHandler;
    }

    public ReplicaSet getReplicaSet(KubernetesConfiguration config,
                                    List<ImageConfiguration> images) throws IOException {
        return new ReplicaSetBuilder()
            .withMetadata(createRsMetaData(config))
            .withSpec(createRsSpec(config, images))
            .build();
    }

    // ===========================================================

    private ObjectMeta createRsMetaData(KubernetesConfiguration config) {
        return new ObjectMetaBuilder()
            .withName(KubernetesHelper.validateKubernetesId(config.getReplicaSetName(), "replication controller name"))
            .withAnnotations(config.getAnnotations().getReplicaSet())
            .build();
    }

    private ReplicaSetSpec createRsSpec(KubernetesConfiguration config, List<ImageConfiguration> images) throws IOException {
        return new ReplicaSetSpecBuilder()
            .withReplicas(config.getReplicas())
            .withTemplate(podTemplateHandler.getPodTemplate(config,images))
            .build();
    }

    private LabelSelector createLabelSelector(Map<String, String> labelMap) {
        LabelSelectorBuilder builder = new LabelSelectorBuilder();
        builder.withMatchLabels(labelMap);
        return builder.build();
    }
}
