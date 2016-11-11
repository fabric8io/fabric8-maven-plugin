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

import java.util.List;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.extensions.*;
import io.fabric8.maven.core.handler.*;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.KubernetesResourceUtil;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.utils.Lists;

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
public class DefaultControllerEnricher extends BaseEnricher {
    protected static final String[] POD_CONTROLLER_KINDS =
        { "ReplicationController", "ReplicaSet", "Deployment", "DeploymentConfig", "PetSet", "DaemonSet" };

    private final DeploymentHandler deployHandler;
    private final ReplicationControllerHandler rcHandler;
    private final ReplicaSetHandler rsHandler;
    private final PetSetHandler petSetHandler;
    private final DaemonSetHandler daemonSetHandler;

    // Available configuration keys
    private enum Config implements Configs.Key {
        name,
        pullPolicy           {{ d = "IfNotPresent"; }},
        type                 {{ d = "deployment"; }};

        public String def() { return d; } protected String d;
    }

    public DefaultControllerEnricher(EnricherContext buildContext) {
        super(buildContext, "fmp-controller");

        HandlerHub handlers = new HandlerHub(buildContext.getProject());
        rcHandler = handlers.getReplicationControllerHandler();
        rsHandler = handlers.getReplicaSetHandler();
        deployHandler = handlers.getDeploymentHandler();
        petSetHandler = handlers.getPetSetHandler();
        daemonSetHandler = handlers.getDaemonSetHandler();
    }

    @Override
    public void addMissingResources(KubernetesListBuilder builder) {
        final String name = getConfig(Config.name, MavenUtil.createDefaultResourceName(getProject()));
        ResourceConfig config =
            new ResourceConfig.Builder()
                .controllerName(name)
                .imagePullPolicy(getConfig(Config.pullPolicy))
                .build();

        final List<ImageConfiguration> images = getImages();

        final Deployment defaultDeployment = deployHandler.getDeployment(config, images);

        // Check if at least a replica set is added. If not add a default one
        if (!KubernetesResourceUtil.checkForKind(builder, POD_CONTROLLER_KINDS)) {
            // At least one image must be present, otherwise the resulting config will be invalid
            if (!Lists.isNullOrEmpty(images)) {
                String type = getConfig(Config.type);
                if (type.equalsIgnoreCase("deployment")) {
                    log.info("Adding a default Deployment");
                    builder.addToDeploymentItems(defaultDeployment);
                } else if (type.equalsIgnoreCase("petSet")) {
                    log.info("Adding a default PetSet");
                    builder.addToPetSetItems(petSetHandler.getPetSet(config, images));
                } else if (type.equalsIgnoreCase("daemonSet")) {
                    log.info("Adding a default DaemonSet");
                    builder.addToDaemonSetItems(daemonSetHandler.getDaemonSet(config, images));
                } else if (type.equalsIgnoreCase("replicaSet")) {
                    log.info("Adding a default ReplicaSet");
                    builder.addToReplicaSetItems(rsHandler.getReplicaSet(config, images));
                } else if (type.equalsIgnoreCase("replicationController")) {
                    log.info("Adding a default ReplicationController");
                    builder.addToReplicationControllerItems(rcHandler.getReplicationController(config, images));
                }
            }
        } else {
            final DeploymentSpec spec = defaultDeployment.getSpec();
            if (spec != null) {
                PodTemplateSpec template = spec.getTemplate();
                if (template != null) {
                    final PodSpec podSpec = template.getSpec();
                    if (podSpec != null) {
                        builder.accept(new TypedVisitor<PodSpecBuilder>() {
                            @Override
                            public void visit(PodSpecBuilder builder) {
                                KubernetesResourceUtil.mergePodSpec(builder, podSpec, name);
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
                                mergeDeploymentSpec(builder, spec);
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
                                        KubernetesResourceUtil.mergePodSpec(podSpecBuilder, podSpec, name);
                                    }
                                }
                            }
                        });
                    }
                }
            }
        }
    }

    private void mergeDeploymentSpec(DeploymentBuilder builder, DeploymentSpec spec) {
        DeploymentFluent.SpecNested<DeploymentBuilder> specBuilder = builder.editSpec();
        KubernetesResourceUtil.mergeSimpleFields(specBuilder, spec);
        specBuilder.endSpec();
    }


    static {
        KubernetesResourceUtil.SIMPLE_FIELD_TYPES.add(String.class);
        KubernetesResourceUtil.SIMPLE_FIELD_TYPES.add(Double.class);
        KubernetesResourceUtil.SIMPLE_FIELD_TYPES.add(Float.class);
        KubernetesResourceUtil.SIMPLE_FIELD_TYPES.add(Long.class);
        KubernetesResourceUtil.SIMPLE_FIELD_TYPES.add(Integer.class);
        KubernetesResourceUtil.SIMPLE_FIELD_TYPES.add(Short.class);
        KubernetesResourceUtil.SIMPLE_FIELD_TYPES.add(Character.class);
        KubernetesResourceUtil.SIMPLE_FIELD_TYPES.add(Byte.class);
        KubernetesResourceUtil.SIMPLE_FIELD_TYPES.add(double.class);
        KubernetesResourceUtil.SIMPLE_FIELD_TYPES.add(float.class);
        KubernetesResourceUtil.SIMPLE_FIELD_TYPES.add(long.class);
        KubernetesResourceUtil.SIMPLE_FIELD_TYPES.add(int.class);
        KubernetesResourceUtil.SIMPLE_FIELD_TYPES.add(short.class);
        KubernetesResourceUtil.SIMPLE_FIELD_TYPES.add(char.class);
        KubernetesResourceUtil.SIMPLE_FIELD_TYPES.add(byte.class);
    }
}
