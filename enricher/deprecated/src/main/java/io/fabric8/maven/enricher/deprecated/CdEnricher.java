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
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.DaemonSetBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.batch.JobBuilder;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.GitUtil;
import io.fabric8.maven.core.util.kubernetes.Fabric8Annotations;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

/**
 * Enricher for adding continous delivery metadata:
 * <p>
 * <ul>
 * <li>Git access URL</li>
 * <li>Jenkins build url</li>
 * </ul>
 *
 * @since 01/05/16
 */
public class CdEnricher extends AbstractLiveEnricher {

    // Available configuration keys
    private enum Config implements Configs.Key {
        gitService {{
            d ="gogs";
        }},
        jenkinsService {{
            d = "jenkins";
        }},
        gitUserEnvVar {{
            d = "GIT_USER";
        }};

        public String def() {
            return d;
        }

        protected String d;
    }

    public CdEnricher(MavenEnricherContext buildContext) {
        super(buildContext, "f8-deprecated-cd");
    }

    @Override
    protected boolean getDefaultOnline() {
        // If no online mode specified, we are supposed to be online
        // when running in an Jenkins CD.
        // TODO: Isn't there a better datum to detect a CD build ? because BUILD_CD is set
        // also when you do a 'regular' CI job ....
        String buildId = getBuildId();
        return buildId != null;
    }

    private String getBuildId() {
        String buildId = System.getenv("BUILD_ID");
        if (buildId == null) {
            buildId = System.getProperty("BUILD_ID");
        }
        return buildId;
    }

    public Map<String, String> getAnnotations() {
        if (isOnline()) {
            Map<String, String> annotations = new HashMap<>();
            String repoName = getContext().getGav().getArtifactId();
            try (Repository repository = GitUtil.getGitRepository(getContext().getProjectDirectory())) {
                // Git annotations (if git is used as SCM)
                if (repository != null) {
                    String gitCommitId = GitUtil.getGitCommitId(repository);
                    if (gitCommitId != null) {
                        addGitServiceUrl(annotations, repoName, gitCommitId);
                    } else {
                        log.debug("No Git commit id found");
                    }
                } else {
                    log.debug("No local Git repository found");
                }
            } catch (IOException | GitAPIException e) {
                log.error("Cannot extract Git information for adding to annotations: " + e, e);
            }
            // Jenkins annotations
            addJenkinsServiceUrl(annotations, repoName);
            return annotations;

        }
        return null;
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

    // =================================

    private void addGitServiceUrl(Map<String, String> annotations, String repoName, String gitCommitId) {
        String username = getGitUserName();
        // this requires online access to kubernetes so we should silently fail if no connection
        String gogsUrl = getExternalServiceURL(getConfig(Config.gitService), "http");
        String rootGitUrl = String.format("%s/%s/%s",gogsUrl, username, repoName);
        rootGitUrl = String.format("%s/%s/%s",rootGitUrl, "commit", gitCommitId);
        if (StringUtils.isNotBlank(rootGitUrl)) {
            annotations.put(Fabric8Annotations.GIT_URL.value(), rootGitUrl);
        }
    }

    private void addJenkinsServiceUrl(Map<String, String> annotations, String repoName) {
        String buildId = getBuildId();
        if (buildId != null) {
            annotations.put(Fabric8Annotations.BUILD_ID.value(), buildId);
            String serviceUrl = getExternalServiceURL(getConfig(Config.jenkinsService), "http");
            if (serviceUrl != null) {
                String jobUrl = String.format("%s/job/%s/%s",serviceUrl, repoName, buildId);
                annotations.put(Fabric8Annotations.BUILD_URL.value(), jobUrl);
            }
        } else {
            log.debug("No Jenkins annotation as no BUILD_ID could be found");
        }
    }

    private String getGitUserName() {
        String username;
        String userEnvVar = getConfig(Config.gitUserEnvVar);
        username = System.getenv(userEnvVar);
        if (username == null) {
            username = System.getProperty(userEnvVar, "gogsadmin");
        }
        return username;
    }

}
