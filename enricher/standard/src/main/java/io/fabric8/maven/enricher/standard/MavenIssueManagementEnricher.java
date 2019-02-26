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
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.DaemonSetBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.batch.JobBuilder;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.util.kubernetes.Fabric8Annotations;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.project.MavenProject;

/**
 * This enricher will add the maven &gt;IssueManagement&lt; related metadata as annotations
 * the typical values will be like
 * <ul>
 * <li>system</li>
 * <li>url</li>
 * </ul>
 *
 * @author kameshs
 */
public class MavenIssueManagementEnricher extends BaseEnricher {
    static final String ENRICHER_NAME = "fmp-maven-issue-mgmt";

    public MavenIssueManagementEnricher(MavenEnricherContext buildContext) {
        super(buildContext, ENRICHER_NAME);
    }

    @Override
    public void create(PlatformMode platformMode, KubernetesListBuilder builder) {
        builder.accept(new TypedVisitor<ServiceBuilder>() {
            @Override
            public void visit(ServiceBuilder serviceBuilder) {
                serviceBuilder.editMetadata().addToAnnotations(getAnnotations()).endMetadata();
            }
        });

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

    private Map<String, String> getAnnotations() {
        Map<String, String> annotations = new HashMap<>();

        if (getContext() instanceof MavenEnricherContext) {
            MavenEnricherContext mavenEnricherContext = (MavenEnricherContext) getContext();
            MavenProject rootProject = mavenEnricherContext.getProject();
            if (hasIssueManagement(rootProject)) {
                IssueManagement issueManagement = rootProject.getIssueManagement();
                String system = issueManagement.getSystem();
                String url = issueManagement.getUrl();
                if (StringUtils.isNotEmpty(system) && StringUtils.isNotEmpty(url)) {
                    annotations.put(Fabric8Annotations.ISSUE_SYSTEM.value(), system);
                    annotations.put(Fabric8Annotations.ISSUE_TRACKER_URL.value(), url);
                }
            }
        }
        return annotations;
    }

    private boolean hasIssueManagement(MavenProject project) {
        return project.getIssueManagement() != null;
    }

}
