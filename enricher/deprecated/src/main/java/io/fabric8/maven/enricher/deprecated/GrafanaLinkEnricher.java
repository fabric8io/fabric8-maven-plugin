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
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import java.util.Collections;
import java.util.Map;

import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import org.apache.commons.lang3.StringUtils;

/**
 */
public class GrafanaLinkEnricher extends BaseEnricher {

    public GrafanaLinkEnricher(MavenEnricherContext buildContext) {
        super(buildContext, "f8-deprecated-cd-grafana-link");
    }

    private enum Config implements Configs.Key {
        metricsDashboard;

        public String def() { return d; } protected String d;
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
        String url = findGrafanaLink();
        return url != null ? Collections.singletonMap("fabric8.io/metrics-path", url) : null;
    }

    private String findGrafanaLink() {
        String defaultDashboard = detectDefaultDashboard();
        String query = "";
        String projectName = null;
        String version = null;

        // TODO - use the docker names which may differ from project metadata!
        if (StringUtils.isBlank(projectName)) {
            projectName = getContext().getGav().getArtifactId();
        }
        if (StringUtils.isBlank(version)) {
            version = getContext().getGav().getVersion();
        }

        if (StringUtils.isNotBlank(projectName)) {
            query += "&var-project=" + projectName;
        }
        if (StringUtils.isNotBlank(version)) {
            query += "&var-version=" + version;
        }
        if (query.startsWith("&")) {
            query = "?" + query.substring(1);
        }
        return String.format("dashboard/file/%s%s", defaultDashboard, query);
    }

    protected String detectDefaultDashboard() {
        String dashboard = getConfig(Config.metricsDashboard);
        if (StringUtils.isNotBlank(dashboard)) {
            return dashboard;
        }
        if (getContext().getProjectClassLoaders().isClassInCompileClasspath(false,"org.apache.camel.CamelContext")) {
            return "camel-routes.json";
        }
        return "kubernetes-pods.json";
    }

}
