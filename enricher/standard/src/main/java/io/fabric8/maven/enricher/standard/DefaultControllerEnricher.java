/**
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
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.config.RuntimeMode;
import io.fabric8.maven.core.handler.DeploymentHandler;
import io.fabric8.maven.core.handler.DeploymentConfigHandler;
import io.fabric8.maven.core.handler.ReplicationControllerHandler;
import io.fabric8.maven.core.handler.ReplicaSetHandler;
import io.fabric8.maven.core.handler.StatefulSetHandler;
import io.fabric8.maven.core.handler.DaemonSetHandler;
import io.fabric8.maven.core.handler.JobHandler;
import io.fabric8.maven.core.handler.HandlerHub;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.core.util.kubernetes.KubernetesResourceUtil;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import io.fabric8.openshift.api.model.DeploymentConfigBuilder;

import java.util.Collections;
import java.util.List;

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

    private final RuntimeMode runtimeMode;

    private final DeploymentHandler deployHandler;
    private final DeploymentConfigHandler deployConfigHandler;
    private final ReplicationControllerHandler rcHandler;
    private final ReplicaSetHandler rsHandler;
    private final StatefulSetHandler statefulSetHandler;
    private final DaemonSetHandler daemonSetHandler;
    private final JobHandler jobHandler;

    // Available configuration keys
    private enum Config implements Configs.Key {
        name,
        pullPolicy             {{ d = "IfNotPresent"; }},
        type                   {{ d = "deployment"; }},
        replicaCount           {{ d = "1"; }};

        public String def() { return d; } protected String d;
    }

    public DefaultControllerEnricher(MavenEnricherContext buildContext) {
        super(buildContext, "fmp-controller");

        HandlerHub handlers = new HandlerHub(
            getContext().getGav(), getContext().getConfiguration().getProperties());
        runtimeMode = buildContext.getRuntimeMode();
        rcHandler = handlers.getReplicationControllerHandler();
        rsHandler = handlers.getReplicaSetHandler();
        deployHandler = handlers.getDeploymentHandler();
        deployConfigHandler = handlers.getDeploymentConfigHandler();
        statefulSetHandler = handlers.getStatefulSetHandler();
        daemonSetHandler = handlers.getDaemonSetHandler();
        jobHandler = handlers.getJobHandler();
    }

    @Override
    public void addMissingResources(PlatformMode platformMode, KubernetesListBuilder builder) {
        final String name = getConfig(Config.name, MavenUtil.createDefaultResourceName(getContext().getGav().getSanitizedArtifactId()));
        ResourceConfig xmlResourceConfig = getConfiguration().getResource().orElse(null);
        ResourceConfig config = new ResourceConfig.Builder(xmlResourceConfig)
                .controllerName(name)
                .imagePullPolicy(getImagePullPolicy(xmlResourceConfig, getConfig(Config.pullPolicy)))
                .withReplicas(getReplicaCount(xmlResourceConfig, Configs.asInt(getConfig(Config.replicaCount))))
                .build();

        final List<ImageConfiguration> images = getImages().orElse(Collections.emptyList());

        // Check if at least a replica set is added. If not add a default one
        if (!KubernetesResourceUtil.checkForKind(builder, POD_CONTROLLER_KINDS)) {
            // At least one image must be present, otherwise the resulting config will be invalid
            if (!images.isEmpty()) {
                String type = getConfig(Config.type);
                if ("deployment".equalsIgnoreCase(type) || "deploymentConfig".equalsIgnoreCase(type)) {
                    if (platformMode == platformMode.kubernetes) {
                        log.info("Adding a default Deployment");
                        builder.addToDeploymentItems(deployHandler.getDeployment(config, images));
                    } else {
                        log.info("Adding a default DeploymentConfig");
                        builder.addToDeploymentConfigItems(deployConfigHandler.getDeploymentConfig(config, images, getOpenshiftDeployTimeoutInSeconds(3600L),runtimeMode, isAutomaticTriggerEnabled(true)));
                    }
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
        }
    }

    @Override
    public void adapt(PlatformMode platformMode, KubernetesListBuilder builder) {
        if(platformMode == PlatformMode.kubernetes) {
            builder.accept(new TypedVisitor<DeploymentBuilder>() {
                @Override
                public void visit(DeploymentBuilder deploymentBuilder) {
                    deploymentBuilder.editSpec().withReplicas(Integer.parseInt(getConfig(Config.replicaCount))).endSpec();
                }
            });
        } else {
            builder.accept(new TypedVisitor<DeploymentConfigBuilder>() {
                @Override
                public void visit(DeploymentConfigBuilder deploymentConfigBuilder) {
                    deploymentConfigBuilder.editSpec().withReplicas(Integer.parseInt(getConfig(Config.replicaCount))).endSpec();

                    if(isAutomaticTriggerEnabled(true)) {
                        deploymentConfigBuilder.editSpec().addNewTrigger().withType("ConfigChange").endTrigger().endSpec();
                    }
                }
            });
        }
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

    /**
     * This method just makes sure that the replica count provided in XML config
     * overrides the default option.
     *
     * @param xmlResourceConfig
     * @param defaultValue
     * @return
     */
    private int getReplicaCount(ResourceConfig xmlResourceConfig, int defaultValue) {
        if(xmlResourceConfig != null) {
                return xmlResourceConfig.getReplicas() > 0 ? xmlResourceConfig.getReplicas() : defaultValue;
        }
        return defaultValue;
    }

    /**
     * This method overrides the ImagePullPolicy value by the value provided in
     * XML config.
     *
     * @param resourceConfig
     * @param defaultValue
     * @return
     */
    private String getImagePullPolicy(ResourceConfig resourceConfig, String defaultValue) {
        if(resourceConfig != null) {
            return resourceConfig.getImagePullPolicy() != null ? resourceConfig.getImagePullPolicy() : defaultValue;
        }
        return defaultValue;
    }

    private Boolean isAutomaticTriggerEnabled(Boolean defaultValue) {
        if(getContext().getProperty("fabric8.openshift.enableAutomaticTrigger") != null) {
            return Boolean.parseBoolean(getContext().getProperty("fabric8.openshift.enableAutomaticTrigger").toString());
        } else {
            return defaultValue;
        }
    }

    private Long getOpenshiftDeployTimeoutInSeconds(Long defaultValue) {
        if (getContext().getProperty("fabric8.openshift.deployTimeoutSeconds") != null) {
            return Long.parseLong(getContext().getProperty("fabric8.openshift.deployTimeoutSeconds").toString());
        } else {
            return defaultValue;
        }
    }
}