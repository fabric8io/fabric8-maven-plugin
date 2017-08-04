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

package io.fabric8.maven.core.config;

import org.apache.maven.plugins.annotations.Parameter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author roland
 * @since 22/03/16
 */
public class ResourceConfig {

    @Parameter
    private Map<String, String> env;

    @Parameter
    private MetaDataConfig labels = new MetaDataConfig();

    @Parameter
    private MetaDataConfig annotations = new MetaDataConfig();
    ;

    @Parameter
    private List<VolumeConfig> volumes;

    @Parameter
    private List<SecretConfig> secrets;

    @Parameter(defaultValue = "${project.artifactId}")
    private String controllerName;

    @Parameter
    private List<ServiceConfig> services;

    @Parameter
    private ProbeConfig liveness;

    @Parameter
    private ProbeConfig readiness;

    @Parameter
    private MetricsConfig metrics;

    // Run container in privileged mode
    @Parameter
    private boolean containerPrivileged = false;

    // How images should be pulled (maps to ImagePullPolicy)
    @Parameter
    private String imagePullPolicy;

    // Mapping of port to names
    @Parameter
    private Map<String, Integer> ports;

    // Number of replicas to create
    @Parameter
    private int replicas = 1;

    // Service account to use
    @Parameter
    private String serviceAccount;

    @Parameter
    private String namespace;

    public Map<String, String> getEnv() {
        return env != null ? env : Collections.<String, String>emptyMap();
    }

    public MetaDataConfig getLabels() {
        return labels;
    }

    public MetaDataConfig getAnnotations() {
        return annotations;
    }

    public List<VolumeConfig> getVolumes() {
        return volumes;
    }

    public List<ServiceConfig> getServices() {
        return services;
    }

    public List<SecretConfig> getSecrets() { return secrets; }

    public ProbeConfig getLiveness() {
        return liveness;
    }

    public ProbeConfig getReadiness() {
        return readiness;
    }

    public MetricsConfig getMetrics() {
        return metrics;
    }

    public boolean isContainerPrivileged() {
        return containerPrivileged;
    }

    public String getImagePullPolicy() {
        return imagePullPolicy;
    }

    public String getControllerName() {
        return controllerName;
    }

    public Map<String, Integer> getPorts() {
        return ports;
    }

    public int getReplicas() {
        return replicas;
    }

    public String getServiceAccount() {
        return serviceAccount;
    }

    public String getNamespace() {
        return namespace;
    }

    // =============================================================================================

    public static class Builder {
        private ResourceConfig config = new ResourceConfig();

        public Builder env(Map<String, String> env) {
            config.env = env;
            return this;
        }

        public Builder controllerName(String name) {
            config.controllerName = name;
            return this;
        }

        public Builder imagePullPolicy(String policy) {
            config.imagePullPolicy = policy;
            return this;
        }

        public Builder withReplicas(int replicas) {
            config.replicas = replicas;
            return this;
        }

        public ResourceConfig build() {
            return config;
        }


    }

    // TODO: SCC

    // ===============================
    // TODO:
    // fabric8.extended.environment.metadata
    // fabric8.envProperties
    // fabric8.combineDependencies
    // fabric8.combineJson.target
    // fabric8.combineJson.project

    // fabric8.container.name	 --> alias name ?
    // fabric8.replicationController.name

    // fabric8.iconRef
    // fabric8.iconUrl
    // fabric8.iconUrlPrefix
    // fabric8.iconUrlPrefix

    // fabric8.imagePullPolicySnapshot

    // fabric8.includeAllEnvironmentVariables
    // fabric8.includeNamespaceEnvVar

    // fabric8.namespaceEnvVar

    // fabric8.provider
}
