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

package io.fabric8.maven.enricher.standard;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpecFluent;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.extensions.DeploymentFluent;
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpec;
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpecBuilder;
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpecFluent;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.handler.DeploymentHandler;
import io.fabric8.maven.core.handler.HandlerHub;
import io.fabric8.maven.core.handler.ReplicaSetHandler;
import io.fabric8.maven.core.handler.ReplicationControllerHandler;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.KubernetesResourceUtil;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.fabric8.utils.Strings.isNullOrBlank;

/**
 * Enrich with controller if not already present.
 *
 * By default the following objects will be added
 *
 * <ul>
 *     <li>ReplicationController</li>
 *     <li>ReplicaSet</li>
 *     <li>Deployment (for Kubernetes)</li>
 *     <li>DeploymentConfig (for OpenShift)</li>
 * </ul>
 *
 * TODO: There is a certain overlap with the ImageEnricher with adding default images etc.. This must be resolved.
 *
 * @author roland
 * @since 25/05/16
 */
public class ControllerEnricher extends BaseEnricher {
    protected static final String[] POD_CONTROLLER_KINDS =
        { "ReplicationController", "ReplicaSet", "Deployment", "DeploymentConfig" };

    private final DeploymentHandler deployHandler;
    private final ReplicationControllerHandler rcHandler;
    private final ReplicaSetHandler rsHandler;

    // Available configuration keys
    private enum Config implements Configs.Key {
        name,
        pullPolicy           {{ d = "IfNotPresent"; }},
        type                 {{ d = "deployment"; }};

        public String def() { return d; } protected String d;
    }

    public ControllerEnricher(EnricherContext buildContext) {
        super(buildContext, "fmp-controller");

        HandlerHub handlers = new HandlerHub(buildContext.getProject());
        rcHandler = handlers.getReplicationControllerHandler();
        rsHandler = handlers.getReplicaSetHandler();
        deployHandler = handlers.getDeploymentHandler();
    }

    @Override
    public void addMissingResources(KubernetesListBuilder builder) {
        final String defaultName = getConfig(Config.name, MavenUtil.createDefaultResourceName(getProject()));
        ResourceConfig config =
            new ResourceConfig.Builder()
                .replicaSetName(defaultName)
                .imagePullPolicy(getConfig(Config.pullPolicy))
                .build();
        final Deployment defaultDeployment = deployHandler.getDeployment(config, getImages());

        // Check if at least a replica set is added. If not add a default one
        if (KubernetesResourceUtil.checkForKind(builder, POD_CONTROLLER_KINDS)) {
            final DeploymentSpec spec = defaultDeployment.getSpec();
            if (spec != null) {
                PodTemplateSpec template = spec.getTemplate();
                if (template != null) {
                    final PodSpec podSpec = template.getSpec();
                    if (podSpec != null) {
                        builder.accept(new TypedVisitor<PodSpecBuilder>() {
                            @Override
                            public void visit(PodSpecBuilder builder) {
                                mergePodSpec(builder, podSpec, defaultName);
                            }
                        });

                        // handle Deployment YAML which may not have a DeploymentSpec, PodTemplateSpec or PodSpec
                        // or if it does lets enrich with the defaults
                        builder.accept(new TypedVisitor<DeploymentBuilder>() {
                            @Override
                            public void visit(DeploymentBuilder builder) {
                                DeploymentSpec deploymentSpec = builder.getSpec();
                                if (deploymentSpec == null) {
                                    builder.withNewSpec().endSpec();
                                    deploymentSpec = builder.getSpec();
                                }
                                PodTemplateSpec template = deploymentSpec.getTemplate();
                                DeploymentFluent.SpecNested<DeploymentBuilder> specBuilder = builder.editSpec();
                                if (template == null) {
                                    specBuilder.withNewTemplate().withNewSpecLike(podSpec).endSpec().endTemplate().endSpec();
                                } else {
                                    PodSpec builderSpec = template.getSpec();
                                    if (builderSpec == null) {
                                        specBuilder.editTemplate().withNewSpecLike(podSpec).endSpec().endTemplate().endSpec();
                                    } else {
                                        PodSpecBuilder podSpecBuilder = new PodSpecBuilder(builderSpec);
                                        mergePodSpec(podSpecBuilder, podSpec, defaultName);
                                    }
                                }
                            }
                        });
                    }
                }
            }
        } else {
            String type = getConfig(Config.type);
            if (type.equalsIgnoreCase("deployment")) {
                log.info("Adding a default Deployment");
                builder.addToDeploymentItems(defaultDeployment);
            } else if (type.equalsIgnoreCase("replicaSet")) {
                log.info("Adding a default ReplicaSet");
                builder.addToReplicaSetItems(rsHandler.getReplicaSet(config, getImages()));
            } else if (type.equalsIgnoreCase("replicationController")) {
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
                    if (isNullOrBlank(container.getImagePullPolicy())) {
                        container.setImagePullPolicy(defaultContainer.getImagePullPolicy());
                    }
                    if (isNullOrBlank(container.getImage())) {
                        container.setImage(defaultContainer.getImage());
                    }
                    if (isNullOrBlank(container.getName())) {
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
            if (isNullOrBlank(container.getName())) {
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

}
