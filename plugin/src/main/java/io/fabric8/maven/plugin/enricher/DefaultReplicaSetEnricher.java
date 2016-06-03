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

import io.fabric8.kubernetes.api.builder.Visitor;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpec;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetSpec;
import io.fabric8.maven.core.config.ResourceConfiguration;
import io.fabric8.maven.core.handler.DeploymentHandler;
import io.fabric8.maven.core.handler.HandlerHub;
import io.fabric8.maven.core.handler.ReplicaSetHandler;
import io.fabric8.maven.core.handler.ReplicationControllerHandler;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherConfiguration;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.utils.Maps;
import io.fabric8.utils.Strings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author roland
 * @since 25/05/16
 */
public class DefaultReplicaSetEnricher extends BaseEnricher {
    protected static final String[] POD_CONTROLLER_KINDS = {"ReplicationController", "ReplicaSet", "Deployment", "DeploymentConfig"};

    private final DeploymentHandler deployHandler;
    private final ReplicationControllerHandler rcHandler;
    private final ReplicaSetHandler rsHandler;

    // ==========================================================================
    // Possible configuration options for this enricher
    private enum Config implements EnricherConfiguration.ConfigKey {
        name,
        imagePullPolicy("IfNotPresent"),
        deployment("true"),
        replicaSet("true");

        // Don't beat me ;-) But its boilerplate anyway ....
        private String d; Config() { this(null); } Config(String v) { d = v; } public String defVal() { return d; }
    }
    // ==========================================================================

    public DefaultReplicaSetEnricher(EnricherContext buildContext) {
        super(buildContext);
        HandlerHub handlers = new HandlerHub(buildContext.getProject());
        rcHandler = handlers.getReplicationControllerHandler();
        rsHandler = handlers.getReplicaSetHandler();
        deployHandler = handlers.getDeploymentHandler();
    }

    @Override
    public String getName() {
        return "default.deployment";
    }

    @Override
    public void enrich(KubernetesListBuilder builder) {
        final String defaultName = getConfig(Config.name, MavenUtil.createDefaultResourceName(getProject()));
        ResourceConfiguration config =
            new ResourceConfiguration.Builder()
                .replicaSetName(defaultName)
                .imagePullPolicy(getConfig(Config.imagePullPolicy))
                .build();
        final Deployment defaultDeployment = deployHandler.getDeployment(config, getImages());

        // Check if at least a replica set is added. If not add a default one
        if (hasPodControllers(builder)) {
            final DeploymentSpec spec = defaultDeployment.getSpec();
            if (spec != null) {
                PodTemplateSpec template = spec.getTemplate();
                if (template != null) {
                    final PodSpec podSpec = template.getSpec();
                    if (podSpec != null) {
                        builder.accept(new Visitor<PodSpecBuilder>() {
                            @Override
                            public void visit(PodSpecBuilder builder) {
                                mergePodSpec(builder, podSpec, defaultName);
                            }
                        });
                    }
                }
            }
        } else {
            if (asBoolean(getConfig(Config.deployment))) {
                log.info("Adding a default Deployment");
                builder.addToDeploymentItems(defaultDeployment);
            } else if (asBoolean(getConfig(Config.replicaSet))) {
                log.info("Adding a default ReplicaSet");
                builder.addToReplicaSetItems(rsHandler.getReplicaSet(config, getImages()));
            } else {
                log.info("Adding a default ReplicationController");
                builder.addToReplicationControllerItems(rcHandler.getReplicationController(config, getImages()));
            }
        }
    }

    private void mergePodSpec(PodSpecBuilder builder, PodSpec defaultPodSpec, String defaultName) {
        List<Container> containers = builder.getContainers();
        List<Container> defaultContainers = defaultPodSpec.getContainers();
        int size = defaultContainers.size();
        if (size > 0) {
            if (containers == null || containers.isEmpty()) {
                builder.addToContainers(defaultContainers.toArray(new Container[size]));
            } else {
                int idx = 0;
                for (Container defaultContainer : defaultContainers) {
                    Container container;
                    if (idx < containers.size()) {
                        container = containers.get(idx);
                    } else {
                        container = new Container();
                        containers.add(container);
                    }
                    if (Strings.isNullOrBlank(container.getImagePullPolicy())) {
                        container.setImagePullPolicy(defaultContainer.getImagePullPolicy());
                    }
                    if (Strings.isNullOrBlank(container.getImage())) {
                        container.setImage(defaultContainer.getImage());
                    }
                    if (Strings.isNullOrBlank(container.getName())) {
                        container.setName(defaultContainer.getName());
                    }
                    List<EnvVar> defaultEnv = defaultContainer.getEnv();
                    if (defaultEnv != null) {
                        for (EnvVar envVar : defaultEnv) {
                            ensureHasEnv(container, envVar);
                        }
                    }
                    idx++;
                }
                builder.withContainers(containers);
            }
        } else if (!containers.isEmpty()) {
            // lets default the container name if there's none specified in the custom yaml file
            Container container = containers.get(0);
            if (Strings.isNullOrBlank(container.getName())) {
                container.setName(defaultName);
            }
            builder.withContainers(containers);
        }
    }

    private void ensureHasEnv(Container container, EnvVar envVar) {
        List<EnvVar> envVars = container.getEnv();
        if (envVars == null) {
            envVars = new ArrayList<>();
            container.setEnv(envVars);
        }
        for (EnvVar var : envVars) {
            if (Objects.equals(var.getName(), envVar.getName())) {
                return;
            }
        }
        envVars.add(envVar);
    }

    private boolean hasPodControllers(KubernetesListBuilder builder) {
        return checkForKind(builder, POD_CONTROLLER_KINDS);
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
