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

package io.fabric8.maven.core.access;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.client.*;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.utils.Strings;

import static io.fabric8.kubernetes.api.KubernetesHelper.DEFAULT_NAMESPACE;

/**
 * @author roland
 * @since 17/07/16
 */
public class ClusterAccess {

    private String namespace;

    public ClusterAccess(String namespace) {
        if (Strings.isNullOrBlank(namespace)) {
            this.namespace = KubernetesHelper.defaultNamespace();
        }
        if (Strings.isNullOrBlank(namespace)) {
            this.namespace = DEFAULT_NAMESPACE;
        }
    }

    public KubernetesClient createKubernetesClient() {
        Config config = createDefaultConfig();
        return new DefaultKubernetesClient(createDefaultConfig());
    }

    public OpenShiftClient createOpenShiftClient() {
        return new DefaultOpenShiftClient(createDefaultConfig());
    }

    // ============================================================================

    private Config createDefaultConfig() {
        return new ConfigBuilder().withNamespace(getNamespace()).build();
    }
    public String getNamespace() {
        return namespace;
    }
}

