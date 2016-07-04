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

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.maven.core.config.ResourceConfiguration;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.utils.Strings;
import org.apache.maven.project.MavenProject;

import java.util.List;
import java.util.Map;

import static io.fabric8.kubernetes.api.KubernetesHelper.DEFAULT_NAMESPACE;
import static java.rmi.server.RemoteServer.getLog;

/**
 * @author roland
 * @since 01/04/16
 */
public abstract class BaseEnricher implements Enricher {

    private final EnricherConfiguration config;
    private final String name;
    private EnricherContext buildContext;
    private KubernetesClient kubernetesClient;

    protected Logger log;

    public BaseEnricher(EnricherContext buildContext, String name) {
        this.buildContext = buildContext;
        // Pick the configuration which is for us
        this.config = new EnricherConfiguration(name, buildContext.getConfig());
        this.log = buildContext.getLog();
        this.name = name;
    }


    private enum Config implements Configs.Key {
        offline {{ d = "false"; }};

        public String def() { return d; } protected String d;
    }


    @Override
    public String getName() {
        return null;
    }

    @Override
    public Map<String, String> getLabels(Kind kind) { return null; }

    @Override
    public Map<String, String> getAnnotations(Kind kind) { return null; }

    @Override
    public void adapt(KubernetesListBuilder builder) { }

    @Override
    public void addDefaultResources(KubernetesListBuilder builder) { }

    @Override
    public Map<String, String> getSelector(Kind kind) { return null; }
    protected MavenProject getProject() {
        return buildContext.getProject();
    }

    protected Logger getLog() {
        return log;
    }

    /**
     * Returns true if in offline mode
     */
    protected boolean isOffline() {
        return Configs.asBoolean(getConfig(Config.offline));
    }


    protected KubernetesClient getKubernetes() {
        if (kubernetesClient == null) {
            String namespace = getNamespaceConfig();
            if (Strings.isNullOrBlank(namespace)) {
                namespace = KubernetesHelper.defaultNamespace();
            }
            if (Strings.isNullOrBlank(namespace)) {
                namespace = DEFAULT_NAMESPACE;
            }
            kubernetesClient = new DefaultKubernetesClient(new ConfigBuilder().withNamespace(namespace).build());
        }
        return kubernetesClient;
    }

    private String getNamespaceConfig() {
        ResourceConfiguration resourceConfiguration = getContext().getResourceConfiguration();
        if (resourceConfiguration != null) {
            return resourceConfiguration.getNamespace();
        }
        return null;
    }

    protected List<ImageConfiguration> getImages() {
        return buildContext.getImages();
    }

    protected String getConfig(Configs.Key key) {
        return config.get(key);
    }

    protected String getConfig(Configs.Key key, String defaultVal) {
        return config.get(key, defaultVal);
    }

    protected EnricherContext getContext() {
        return buildContext;
    }


    /**
     * Returns the external access to the given service name
     *
     * @param serviceName name of the service
     * @param protocol URL protocol such as <code>http</code>
     */
    protected String getExternalServiceURL(String serviceName, String protocol) {
        String publicUrl = null;
        if (isOffline()) {
            getLog().info("Not looking for service " + serviceName + " as in offline mode");
        } else {
            try {
                KubernetesClient kubernetes = getKubernetes();
                String ns = kubernetes.getNamespace();
                Service service = kubernetes.services().inNamespace(ns).withName(serviceName).get();
                if (service != null) {
                    publicUrl = KubernetesHelper.getServiceURL(kubernetes, serviceName, ns, protocol, true);
                }
            } catch (Exception e) {
                getLog().warn("Failed to find service " + serviceName + ". May be in offline mode. Exception: " + e);
            }
        }
        return publicUrl;
    }
}
