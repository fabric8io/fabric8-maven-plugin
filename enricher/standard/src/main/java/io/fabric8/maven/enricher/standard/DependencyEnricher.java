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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import org.apache.maven.artifact.Artifact;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.fabric8.kubernetes.api.KubernetesHelper.getKind;
import static io.fabric8.maven.core.util.Constants.RESOURCE_LOCATION_ANNOTATION;

/**
 * Enricher for embedding dependency descriptors to single package.
 *
 * @author jimmidyson
 * @since 14/07/16
 */
public class DependencyEnricher extends BaseEnricher {
    private static String DEPENDENCY_KUBERNETES_YAML = "META-INF/fabric8/kubernetes.yml";

    private Set<URL> dependencyArtifacts = new HashSet<>();

    // Available configuration keys
    private enum Config implements Configs.Key {

        includeTransitive {{
            d = "true";
        }},
        includePlugin {{
            d = "true";
        }};

        protected String d;

        public String def() {
            return d;
        }
    }

    public DependencyEnricher(EnricherContext buildContext) {
        super(buildContext, "fmp-dependency");

        Set<Artifact> artifacts = isIncludeTransitive() ?
                buildContext.getProject().getArtifacts() : buildContext.getProject().getDependencyArtifacts();

        for (Artifact artifact : artifacts) {
            if (Artifact.SCOPE_COMPILE.equals(artifact.getScope()) && "jar".equals(artifact.getType())) {
                File file = artifact.getFile();
                try {
                    URL url = new URL("jar:" + file.toURI().toURL() + "!/" + DEPENDENCY_KUBERNETES_YAML);
                    dependencyArtifacts.add(url);
                } catch (MalformedURLException e) {
                    getLog().debug("Failed to create URL for " + file + ": " + e, e);
                }
            }
        }
        // lets look on the current plugin classpath too
        if (isIncludePlugin()) {
            Enumeration<URL> resources = null;
            try {
                resources = getClass().getClassLoader().getResources(DEPENDENCY_KUBERNETES_YAML);
            } catch (IOException e) {
                getLog().error("Could not find " + DEPENDENCY_KUBERNETES_YAML + " on the classpath: " + e, e);
            }
            if (resources != null) {
                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    dependencyArtifacts.add(url);
                }
            }
        }

    }

    @Override
    public void adapt(KubernetesListBuilder builder) {
        for (URL url : dependencyArtifacts) {
            try {
                InputStream is = url.openStream();
                if (is != null) {
                    log.debug("Processing Kubernetes YAML in at: " + url);

                    KubernetesList resources = new ObjectMapper(new YAMLFactory()).readValue(is, KubernetesList.class);
                    List<HasMetadata> items = resources.getItems();
                    for (HasMetadata item : items) {
                        Map<String, String> annotations = KubernetesHelper.getOrCreateAnnotations(item);
                        if (!annotations.containsKey(RESOURCE_LOCATION_ANNOTATION)) {
                            annotations.put(RESOURCE_LOCATION_ANNOTATION, url.toString());
                        }
                        log.debug("  found " + getKind(item) + "  " + KubernetesHelper.getName(item));
                    }
                    builder.addToItems(items.toArray(new HasMetadata[0]));
                }
            } catch (IOException e) {
                getLog().debug("Skipping " + url + ": " + e, e);
            }
        }
    }

    protected boolean isIncludePlugin() {
        return Configs.asBoolean(getConfig(Config.includePlugin));
    }

    protected boolean isIncludeTransitive() {
        return Configs.asBoolean(getConfig(Config.includeTransitive));
    }


}
