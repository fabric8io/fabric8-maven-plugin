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
import io.fabric8.kubernetes.api.model.extensions.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.extensions.DeploymentFluent;
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpec;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetFluent;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetSpec;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.handler.DaemonSetHandler;
import io.fabric8.maven.core.handler.DeploymentHandler;
import io.fabric8.maven.core.handler.HandlerHub;
import io.fabric8.maven.core.handler.JobHandler;
import io.fabric8.maven.core.handler.ReplicaSetHandler;
import io.fabric8.maven.core.handler.ReplicationControllerHandler;
import io.fabric8.maven.core.handler.StatefulSetHandler;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.core.util.kubernetes.KubernetesResourceUtil;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;

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
        { "ReplicationController", "ReplicaSet", "Deployment", "DeploymentConfig", "StatefulSet", "DaemonSet", "Job" };

    private final DeploymentHandler deployHandler;
    private final ReplicationControllerHandler rcHandler;
    private final ReplicaSetHandler rsHandler;
    private final StatefulSetHandler statefulSetHandler;
    private final DaemonSetHandler daemonSetHandler;
    private final JobHandler jobHandler;

    // Available configuration keys
    private enum Config implements Configs.Key {
        name,
        pullPolicy           {{ d = "IfNotPresent"; }},
        type                 {{ d = "deployment"; }},
        replicaCount         {{ d = "1"; }};

        public String def() { return d; } protected String d;
    }

    public DefaultControllerEnricher(EnricherContext buildContext) {
        super(buildContext, "fmp-controller");

        HandlerHub handlers = new HandlerHub(buildContext.getProject());
        rcHandler = handlers.getReplicationControllerHandler();
        rsHandler = handlers.getReplicaSetHandler();
        deployHandler = handlers.getDeploymentHandler();
        statefulSetHandler = handlers.getStatefulSetHandler();
        daemonSetHandler = handlers.getDaemonSetHandler();
        jobHandler = handlers.getJobHandler();
    }

    @Override
    public void addMissingResources(KubernetesListBuilder builder) {
        final String name = getConfig(Config.name, MavenUtil.createDefaultResourceName(getProject()));
        final ResourceConfig config = new ResourceConfig.Builder()
                    .controllerName(name)
                    .imagePullPolicy(getConfig(Config.pullPolicy))
                    .withReplicas(Configs.asInt(getConfig(Config.replicaCount)))
                    .build();

        final List<ImageConfiguration> images = getImages();

        // Check if at least a replica set is added. If not add a default one
        if (!KubernetesResourceUtil.checkForKind(builder, POD_CONTROLLER_KINDS)) {
            // At least one image must be present, otherwise the resulting config will be invalid
            if (images != null && !images.isEmpty()) {
                String type = getConfig(Config.type);
                if ("deployment".equalsIgnoreCase(type)) {
                    log.info("Adding a default Deployment");
                    builder.addToDeploymentItems(deployHandler.getDeployment(config, images));
                } else if ("statefulSet".equalsIgnoreCase(type)) {
                    log.info("Adding a default StatefulSet");
                    builder.addToStatefulSetItems(statefulSetHandler.getStatefulSet(config, images));
                } else if ("daemonSet".equalsIgnoreCase(type)) {
                    log.info("Adding a default DaemonSet");
                    builder.addToDaemonSetItems(daemonSetHandler.getDaemonSet(config, images));
                } else if ("replicaSet".equalsIgnoreCase(type)) {
                    log.info("Adding a default ReplicaSet");
                    builder.addToReplicaSetItems(rsHandler.getReplicaSet(config, images));
                } else if ("replicationController".equalsIgnoreCase(type)) {
                    log.info("Adding a default ReplicationController");
                    builder.addToReplicationControllerItems(rcHandler.getReplicationController(config, images));
                } else if ("job".equalsIgnoreCase(type)) {
                    log.info("Adding a default Job");
                    builder.addToJobItems(jobHandler.getJob(config, images));
                }
            }
        } else if (KubernetesResourceUtil.checkForKind(builder, "StatefulSet")) {
            final StatefulSetSpec spec = statefulSetHandler.getStatefulSet(config, images).getSpec();
            if (spec != null) {
                builder.accept(new TypedVisitor<StatefulSetBuilder>() {
                    @Override
                    public void visit(StatefulSetBuilder statefulSetBuilder) {
                        statefulSetBuilder.editOrNewSpec().editOrNewTemplate().editOrNewSpec().endSpec().endTemplate().endSpec();
                        mergeStatefulSetSpec(statefulSetBuilder, spec);
                    }
                });

                if (spec.getTemplate() != null && spec.getTemplate().getSpec() != null) {
                    final PodSpec podSpec = spec.getTemplate().getSpec();
                    builder.accept(new TypedVisitor<PodSpecBuilder>() {
                        @Override
                        public void visit(PodSpecBuilder builder) {
                            KubernetesResourceUtil.mergePodSpec(builder, podSpec, name);
                        }
                    });
                }
            }
        } else {
            final DeploymentSpec spec = deployHandler.getDeployment(config, images).getSpec();
            if (spec != null) {
                builder.accept(new TypedVisitor<DeploymentBuilder>() {
                    @Override
                    public void visit(DeploymentBuilder deploymentBuilder) {
                        deploymentBuilder.editOrNewSpec().editOrNewTemplate().editOrNewSpec().endSpec().endTemplate().endSpec();
                        mergeDeploymentSpec(deploymentBuilder, spec);
                    }
                });

                if (spec.getTemplate() != null && spec.getTemplate().getSpec() != null) {
                    final PodSpec podSpec = spec.getTemplate().getSpec();
                    builder.accept(new TypedVisitor<PodSpecBuilder>() {
                        @Override
                        public void visit(PodSpecBuilder builder) {
                            KubernetesResourceUtil.mergePodSpec(builder, podSpec, name);
                        }
                    });
                }
            }
        }
    }

    private void mergeDeploymentSpec(DeploymentBuilder builder, DeploymentSpec spec) {
        DeploymentFluent.SpecNested<DeploymentBuilder> specBuilder = builder.editSpec();
        KubernetesResourceUtil.mergeSimpleFields(specBuilder, spec);
        specBuilder.endSpec();
    }

    private void mergeStatefulSetSpec(StatefulSetBuilder builder, StatefulSetSpec spec) {
        StatefulSetFluent.SpecNested<StatefulSetBuilder> specBuilder = builder.editSpec();
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
