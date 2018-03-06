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

package io.fabric8.maven.plugin.converter;

import java.util.Map;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetSpec;

/**
 * Convert a Kubernetes <code>ReplicaSet</code> to a <code>RelicationController</code> which
 * can be used by OpenShift, too.
 *
 * @author roland
 * @since 01/08/16
 */
public class ReplicSetOpenShiftConverter implements KubernetesToOpenShiftConverter {
    @Override
    public HasMetadata convert(HasMetadata item, boolean trimImageInContainerSpec, boolean enableAutomaticTrigger) {
        ReplicaSet resource = (ReplicaSet) item;
        ReplicationControllerBuilder builder = new ReplicationControllerBuilder();
        builder.withMetadata(resource.getMetadata());
        ReplicaSetSpec spec = resource.getSpec();
        if (spec != null) {
            ReplicationControllerFluent.SpecNested<ReplicationControllerBuilder> specBuilder = builder.withNewSpec();
            Integer replicas = spec.getReplicas();
            if (replicas != null) {
                specBuilder.withReplicas(replicas);
            }
            LabelSelector selector = spec.getSelector();
            if (selector != null) {
                Map<String, String> matchLabels = selector.getMatchLabels();
                if (matchLabels != null && !matchLabels.isEmpty()) {
                    specBuilder.withSelector(matchLabels);
                }
            }
            PodTemplateSpec template = spec.getTemplate();
            if (template != null) {
                specBuilder.withTemplate(template);
            }
            specBuilder.endSpec();
        }
        return builder.build();
    }
}
