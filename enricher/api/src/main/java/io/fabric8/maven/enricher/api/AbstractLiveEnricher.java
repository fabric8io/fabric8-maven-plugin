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

package io.fabric8.maven.enricher.api;

import java.net.ConnectException;
import java.util.*;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.maven.core.config.ResourceConfiguration;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.utils.Strings;

import static io.fabric8.kubernetes.api.KubernetesHelper.DEFAULT_NAMESPACE;

abstract public class AbstractLiveEnricher extends BaseEnricher {

    private KubernetesClient kubernetesClient;

    private enum Config implements Configs.Key {
        online;

        public String def() { return d; } protected String d;
    }

    public AbstractLiveEnricher(EnricherContext buildContext, String name) {
        super(buildContext, name);
    }

    /**
     * Returns true if in offline mode, "false" if not speciied.
     * Can be overriden by
     */
    protected boolean isOnline() {
        String isOnline = getConfig(Config.online);
        if (isOnline != null) {
            return Configs.asBoolean(isOnline);
        }
        Boolean ret = asBooleanFromGlobalProp("fabric8.online");
        return ret != null ? ret : getDefaultOnline();
    }

    /**
     * Return the value to return if no online mode is explicitely specified.
     * Can be overridden, by default it returns <code>false</code>.
     *
     * @return the defaul valuet.
     */
    protected boolean getDefaultOnline() {
        return false;
    }

    /**
     * Returns the external access to the given service name
     *
     * @param serviceName name of the service
     * @param protocol URL protocol such as <code>http</code>
     */
    protected String getExternalServiceURL(String serviceName, String protocol) {
        String publicUrl = null;
        if (!isOnline()) {
            getLog().info("Not looking for service " + serviceName + " as we are in offline mode");
            return null;
        } else {
            try {
                KubernetesClient kubernetes = getKubernetes();
                String ns = kubernetes.getNamespace();
                Service service = kubernetes.services().inNamespace(ns).withName(serviceName).get();
                return service != null ?
                    KubernetesHelper.getServiceURL(kubernetes, serviceName, ns, protocol, true) :
                    null;
            } catch (Throwable e) {
                Throwable cause = e;

                boolean notFound = false;
                boolean connectError = false;
                Stack<Throwable> stack = unfoldExceptions(e);
                while (!stack.isEmpty()) {
                    Throwable t = stack.pop();
                    if (t instanceof ConnectException || "No route to host".equals(t.getMessage())) {
                        getLog().warn("Cannot connect to Kubernetes to find URL for service %s : %s",
                                      serviceName, cause.getMessage());
                        return null;
                    } else if (t instanceof IllegalArgumentException ||
                               t.getMessage() != null && t.getMessage().matches("^No.*found.*$")) {
                        getLog().warn(cause.getMessage());
                        return null;
                    };
                }
                getLog().warn("Cannot find URL for service %s : %s", serviceName, cause.getMessage());
                return null;
            }
        }
    }

    /**
     * Creates an Iterable to walk the exception from the bottom up
     * (the last caused by going upwards to the root exception).
     *
     * @param exception the exception
     * @return the Iterable
     * @see java.lang.Iterable
     */
    protected Stack<Throwable> unfoldExceptions(Throwable exception) {
        Stack<Throwable> throwables = new Stack<>();

        Throwable current = exception;
        // spool to the bottom of the caused by tree
        while (current != null) {
            throwables.push(current);
            current = current.getCause();
        }
        return throwables;
    }

    // ====================================================================================

    // Check a global prop from the project or system props
    protected Boolean asBooleanFromGlobalProp(String prop) {
        String value = getProject().getProperties().getProperty(prop);
        if (value == null) {
            value = System.getProperty(prop);
        }
        return value != null ? Boolean.valueOf(value) : null;
    }

    private KubernetesClient getKubernetes() {
        if (kubernetesClient == null) {
            String namespace = getNamespace();
            kubernetesClient = new DefaultKubernetesClient(new ConfigBuilder().withNamespace(namespace).build());
        }
        return kubernetesClient;
    }

    private String getNamespace() {
        String namespace = getNamespaceConfig();
        if (Strings.isNullOrBlank(namespace)) {
            namespace = KubernetesHelper.defaultNamespace();
        }
        if (Strings.isNullOrBlank(namespace)) {
            namespace = DEFAULT_NAMESPACE;
        }
        return namespace;
    }

    private String getNamespaceConfig() {
        ResourceConfiguration config = getContext().getResourceConfiguration();
        return config != null ? config.getNamespace() : null;
    }

}
