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
package io.fabric8.maven.enricher.deprecated;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder;
import io.fabric8.kubernetes.api.model.apps.DaemonSetBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.batch.JobBuilder;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.util.kubernetes.Fabric8Annotations;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Site;
import org.apache.maven.project.MavenProject;

/**
 * Adds a link to the generated documentation for this microservice so we can link to the versioned docs in the
 * annotations
 */
public class DocLinkEnricher extends AbstractLiveEnricher {

    public DocLinkEnricher(MavenEnricherContext buildContext) {
        super(buildContext, "f8-deprecated-link");
    }

    @Override
    public void create(PlatformMode platformMode, KubernetesListBuilder builder) {
        builder.accept(new TypedVisitor<DeploymentBuilder>() {
            @Override
            public void visit(DeploymentBuilder builder) {
                builder.editMetadata().addToAnnotations(getAnnotations()).endMetadata();
            }
        });

        builder.accept(new TypedVisitor<DeploymentConfigBuilder>() {
            @Override
            public void visit(DeploymentConfigBuilder builder) {
                builder.editMetadata().addToAnnotations(getAnnotations()).endMetadata();
            }
        });

        builder.accept(new TypedVisitor<ReplicaSetBuilder>() {
            @Override
            public void visit(ReplicaSetBuilder builder) {
                builder.editMetadata().addToAnnotations(getAnnotations()).endMetadata();
            }
        });

        builder.accept(new TypedVisitor<ReplicationControllerBuilder>() {
            @Override
            public void visit(ReplicationControllerBuilder builder) {
                builder.editMetadata().addToAnnotations(getAnnotations()).endMetadata();
            }
        });

        builder.accept(new TypedVisitor<DaemonSetBuilder>() {
            @Override
            public void visit(DaemonSetBuilder builder) {
                builder.editMetadata().addToAnnotations(getAnnotations()).endMetadata();
            }
        });

        builder.accept(new TypedVisitor<StatefulSetBuilder>() {
            @Override
            public void visit(StatefulSetBuilder builder) {
                builder.editMetadata().addToAnnotations(getAnnotations()).endMetadata();
            }
        });

        builder.accept(new TypedVisitor<JobBuilder>() {
            @Override
            public void visit(JobBuilder builder) {
                builder.editMetadata().addToAnnotations(getAnnotations()).endMetadata();
            }
        });
    }

    public Map<String, String> getAnnotations() {
        String url = findDocumentationUrl();
        return url != null ? Collections.singletonMap(Fabric8Annotations.DOCS_URL.value(), url) : null;
    }

    private String getDocumentationUrl() {
        if (getContext() instanceof MavenEnricherContext) {
            MavenEnricherContext mavenEnricherContext = (MavenEnricherContext) getContext();
            MavenProject currentProject = mavenEnricherContext.getProject();
            while (currentProject != null) {
                DistributionManagement distributionManagement = currentProject.getDistributionManagement();
                if (distributionManagement != null) {
                    Site site = distributionManagement.getSite();
                    if (site != null) {
                        return site.getUrl();
                    }
                }
                currentProject = currentProject.getParent();
            }
        }
        return null;
    }

    protected String findDocumentationUrl() {

        String url = getDocumentationUrl();
        if (StringUtils.isNotBlank(url)) {
            // lets replace any properties...
            url = replaceProperties(url, getContext().getConfiguration().getProperties());

            // lets convert the internal dns name to a public name
            try {
                String urlToParse = url;
                int idx = url.indexOf("://");
                if (idx > 0) {
                    // lets strip any previous schemes such as "dav:"
                    int idx2 = url.substring(0, idx).lastIndexOf(':');
                    if (idx2 >= 0 && idx2 < idx) {
                        urlToParse = url.substring(idx2 + 1);
                    }
                }
                URL u = new URL(urlToParse);
                String serviceName = u.getHost();
                String protocol = u.getProtocol();
                if (isOnline()) {
                    // lets see if the host name is a service name in which case we'll resolve to the public URL
                    String publicUrl = getExternalServiceURL(serviceName, protocol);
                    if (StringUtils.isNotBlank(publicUrl)) {
                        return String.format("%s/%s", publicUrl, u.getPath());
                    }
                }
            } catch (MalformedURLException e) {
                getLog().error("Failed to parse URL: %s. %s", url, e);
            }
            return url;
        }
        return null;
    }

    /**
     * Replaces all text of the form <code>${foo}</code> with the value in the properties object
     */
    protected static String replaceProperties(String text, Properties properties) {
        Set<Map.Entry<Object, Object>> entries = properties.entrySet();
        for (Map.Entry<Object, Object> entry : entries) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (key != null && value != null) {
                String pattern = "${" + key + "}";
                text = StringUtils.replace(text, pattern, value.toString());
            }
        }
        return text;
    }
}
