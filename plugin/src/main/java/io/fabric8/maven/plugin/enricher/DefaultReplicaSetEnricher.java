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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.core.config.KubernetesConfiguration;
import io.fabric8.maven.core.handler.HandlerHub;
import io.fabric8.maven.core.handler.ReplicaSetHandler;
import io.fabric8.maven.core.handler.ReplicationControllerHandler;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;

import static org.bouncycastle.asn1.x500.style.RFC4519Style.name;

/**
 * @author roland
 * @since 25/05/16
 */
public class DefaultReplicaSetEnricher extends BaseEnricher {

    private ReplicationControllerHandler rcHandler;
    private ReplicaSetHandler rsHandler;

    public DefaultReplicaSetEnricher(EnricherContext buildContext) {
        super(buildContext);
        HandlerHub handlers = new HandlerHub(buildContext.getProject());
        rcHandler = handlers.getReplicationControllerHandler();
        rsHandler = handlers.getReplicaSetHandler();
    }

    @Override
    public String getName() {
        return "default.rs";
    }

    @Override
    public void enrich(KubernetesListBuilder builder) {
        // Check if at least a replica set is added. If not add a default one
        if (!hasPodControllers(builder)) {
            KubernetesConfiguration config =
                new KubernetesConfiguration.Builder()
                    .replicaSetName(getConfig().get("name", MavenUtil.createDefaultResourceName(getProject())))
                    .imagePullPolicy(getConfig().get("imagePullPolicy","IfNotPresent"))
                    .build();

            if (getConfig().getAsBoolean("useReplicaSet",true)) {
                log.info("Adding a default ReplicaSet");
                builder.addToReplicaSetItems(rsHandler.getReplicaSet(config, getImages()));
            } else {
                log.info("Adding a default ReplicationController");
                builder.addToReplicationControllerItems(rcHandler.getReplicationController(config, getImages()));
            }
        }
    }

    private boolean hasPodControllers(KubernetesListBuilder builder) {
        return checkForKind(builder, "ReplicationController", "ReplicaSet");
    }

    private boolean checkForKind(KubernetesListBuilder builder, String ... kinds) {
        Set<String> kindSet = new HashSet<>(Arrays.asList(kinds));
        for (HasMetadata item : builder.getItems()) {
            if (kindSet.contains(item.getKind())) {
                return true;
            }
        }
        return false;
    }

}
