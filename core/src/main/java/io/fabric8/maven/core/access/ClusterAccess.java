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
package io.fabric8.maven.core.access;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.RuntimeMode;
import io.fabric8.maven.core.util.kubernetes.OpenshiftHelper;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftAPIGroups;
import io.fabric8.openshift.client.OpenShiftClient;
import java.net.UnknownHostException;

/**
 * @author roland
 * @since 17/07/16
 */
public class ClusterAccess {

    private ClusterConfiguration clusterConfiguration;

    private KubernetesClient client;

    public ClusterAccess(ClusterConfiguration clusterConfiguration) {
        this.clusterConfiguration = clusterConfiguration;

        if (this.clusterConfiguration == null) {
            this.clusterConfiguration = new ClusterConfiguration.Builder().build();
        }

        this.client = null;
    }

    @Deprecated
    public ClusterAccess(String namespace) {
        ClusterConfiguration.Builder clusterConfigurationBuilder = new ClusterConfiguration.Builder();
        clusterConfigurationBuilder.namespace(namespace);
        this.clusterConfiguration = clusterConfigurationBuilder.build();
        this.client = null;
    }

    public ClusterAccess(ClusterConfiguration clusterConfiguration, KubernetesClient client) {
        this.clusterConfiguration = clusterConfiguration;
        this.client = client;
    }

    public <T extends KubernetesClient> T createDefaultClient(Logger log) {
        if (isOpenShift(log)) {
            return (T) createOpenShiftClient();
        }

        return (T) createKubernetesClient();
    }

    public KubernetesClient createKubernetesClient() {
        return new DefaultKubernetesClient(createDefaultConfig());
    }

    public OpenShiftClient createOpenShiftClient() {
        return new DefaultOpenShiftClient(createDefaultConfig());
    }

    // ============================================================================

    private Config createDefaultConfig() {
        return this.clusterConfiguration.getConfig();
    }

    public String getNamespace() {
        return this.clusterConfiguration.getNamespace();
    }

    /**
     * Returns true if this cluster is a traditional OpenShift cluster with the <code>/oapi</code> REST API
     * or supports the new <code>/apis/image.openshift.io</code> API Group
     */
    public boolean isOpenShiftImageStream(Logger log) {
        if (isOpenShift(log)) {
            OpenShiftClient openShiftClient = null;
            if (this.client == null) {
                openShiftClient = createOpenShiftClient();
            } else if (this.client instanceof OpenShiftClient) {
                openShiftClient = (OpenShiftClient) this.client;
            } else if (this.client.isAdaptable(OpenShiftClient.class)) {
                openShiftClient = client.adapt(OpenShiftClient.class);
            } else {
                return false;
            }
            return openShiftClient.supportsOpenShiftAPIGroup(OpenShiftAPIGroups.IMAGE);
        }
        return false;
    }

    public boolean isOpenShift(Logger log) {
        try {
            return this.client == null ?
                OpenshiftHelper.isOpenShift(createKubernetesClient()) :
                OpenshiftHelper.isOpenShift(this.client);
        } catch (KubernetesClientException exp) {
            Throwable cause = exp.getCause();
            String prefix = cause instanceof UnknownHostException ? "Unknown host " : "";
            log.warn("Cannot access cluster for detecting mode: %s%s",
                     prefix,
                     cause != null ? cause.getMessage() : exp.getMessage());
        }
        return false;
    }

    public RuntimeMode resolveRuntimeMode(RuntimeMode mode, Logger log) {
        RuntimeMode resolvedMode;
        if (mode == null) {
            mode = RuntimeMode.DEFAULT;
        }
        if (mode.isAuto()) {
            resolvedMode = isOpenShiftImageStream(log) ? RuntimeMode.openshift : RuntimeMode.kubernetes;
        } else {
            resolvedMode = mode;
        }
        return resolvedMode;
    }
}

