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

import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerSpec;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpec;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetSpec;
import io.fabric8.maven.core.util.kubernetes.KubernetesHelper;
import io.fabric8.maven.core.util.kubernetes.KubernetesResourceUtil;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigSpec;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.project.MavenProject;

import static io.fabric8.maven.core.util.DebugConstants.ENV_VAR_JAVA_DEBUG;
import static io.fabric8.maven.core.util.DebugConstants.ENV_VAR_JAVA_DEBUG_PORT;
import static io.fabric8.maven.core.util.DebugConstants.ENV_VAR_JAVA_DEBUG_PORT_DEFAULT;

/**
 * Enables debug mode via a maven property
 */
public class DebugEnricher extends BaseEnricher {

    public static final String ENABLE_DEBUG_MAVEN_PROPERTY = "fabric8.debug.enabled";

    public DebugEnricher(EnricherContext buildContext) {
        super(buildContext, "fmp-debug");
    }

    @Override
    public void addMissingResources(KubernetesListBuilder builder) {
        if (debugEnabled()) {
            int count = 0;
            List<HasMetadata> items = builder.getItems();
            if (items != null) {
                for (HasMetadata item : items) {
                    if (enableDebug(item)) {
                        count++;
                    }
                }
            }
            if (count > 0) {
                builder.withItems(items);
            }
            log.verbose("Enabled debugging on " + count + " resource(s) thanks to the " + ENABLE_DEBUG_MAVEN_PROPERTY + " property");
        } else {
            log.verbose("Debugging not enabled. To enable try setting the " + ENABLE_DEBUG_MAVEN_PROPERTY + " maven or system property to 'true'");
        }
    }

    private boolean debugEnabled() {
        MavenProject project = getContext().getProject();
        if (project != null) {
            String value = project.getProperties().getProperty(ENABLE_DEBUG_MAVEN_PROPERTY);
            if (isTrueFlag(value)) {
                return true;
            };
        }
        return isTrueFlag(System.getProperty(ENABLE_DEBUG_MAVEN_PROPERTY));
    }

    private static boolean isTrueFlag(String value) {
        return StringUtils.isNotBlank(value) && value.equals("true");
    }


    private boolean enableDebug(HasMetadata entity) {
        if (entity instanceof Deployment) {
            Deployment resource = (Deployment) entity;
            DeploymentSpec spec = resource.getSpec();
            if (spec != null) {
                return enableDebugging(entity, spec.getTemplate());
            }
        } else if (entity instanceof ReplicaSet) {
            ReplicaSet resource = (ReplicaSet) entity;
            ReplicaSetSpec spec = resource.getSpec();
            if (spec != null) {
                return enableDebugging(entity, spec.getTemplate());
            }
        } else if (entity instanceof ReplicationController) {
            ReplicationController resource = (ReplicationController) entity;
            ReplicationControllerSpec spec = resource.getSpec();
            if (spec != null) {
                return enableDebugging(entity, spec.getTemplate());
            }
        } else if (entity instanceof DeploymentConfig) {
            DeploymentConfig resource = (DeploymentConfig) entity;
            DeploymentConfigSpec spec = resource.getSpec();
            if (spec != null) {
                return enableDebugging(entity, spec.getTemplate());
            }
        }
        return false;
    }

    private boolean enableDebugging(HasMetadata entity, PodTemplateSpec template) {
        if (template != null) {
            PodSpec podSpec = template.getSpec();
            if (podSpec != null) {
                List<Container> containers = podSpec.getContainers();
                if (containers.size() > 0) {
                    Container container = containers.get(0);
                    List<EnvVar> env = container.getEnv();
                    if (env == null) {
                        env = new ArrayList<>();
                    }
                    String remoteDebugPort = KubernetesResourceUtil.getEnvVar(env, ENV_VAR_JAVA_DEBUG_PORT, ENV_VAR_JAVA_DEBUG_PORT_DEFAULT);
                    boolean enabled = false;
                    if (KubernetesResourceUtil.setEnvVar(env, ENV_VAR_JAVA_DEBUG, "true")) {
                        container.setEnv(env);
                        enabled = true;
                    }
                    List<ContainerPort> ports = container.getPorts();
                    if (ports == null) {
                        ports = new ArrayList<>();
                    }
                    if (KubernetesResourceUtil.addPort(ports, remoteDebugPort, "debug", log)) {
                        container.setPorts(ports);
                        enabled = true;
                    }
                    if (enabled) {
                        log.info("Enabling debug on " + KubernetesHelper.getKind(entity) + " " + KubernetesHelper.getName(entity) + " due to the property: " + ENABLE_DEBUG_MAVEN_PROPERTY);
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
