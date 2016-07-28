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

package io.fabric8.maven.enricher.cd;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.Annotations;
import io.fabric8.kubernetes.api.ServiceNames;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.GitUtil;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.enricher.api.AbstractLiveEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;
import io.fabric8.utils.*;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

/**
 * Enricher for adding build metadata:
 * <p>
 * <ul>
 * <li>Git Branch</li>
 * <li>Git Commit ID</li>
 * <li>Git access URL</li>
 * <li>Jenkins build number</li>
 * <li>Jenkins build url</li>
 * </ul>
 *
 * @since 01/05/16
 */
public class CdEnricher extends AbstractLiveEnricher {

    // Available configuration keys
    private enum Config implements Configs.Key {
        gitService {{
            d = ServiceNames.GOGS;
        }},
        jenkinsService {{
            d = ServiceNames.JENKINS;
        }},
        gitUserEnvVar {{
            d = "GIT_USER";
        }};

        public String def() {
            return d;
        }

        protected String d;
    }

    public CdEnricher(EnricherContext buildContext) {
        super(buildContext, "f8-cd");
    }

    @Override
    protected boolean getDefaultOnline() {
        // If no online mode specified, we are supposed to be online
        // when running in an Jenkins CD.
        // TODO: Isn't there a better datum to detect a CD build ? because BUILD_CD is set
        // also when you do a 'regular' CI job ....
        return Systems.getEnvVarOrSystemProperty("BUILD_ID") != null;
    }

    @Override
    public Map<String, String> getAnnotations(Kind kind) {
        if (isOnline() && (kind.isDeployOrReplicaKind() || kind.isService())) {
            Map<String, String> annotations = new HashMap<>();
            MavenProject rootProject = MavenUtil.getRootProject(getProject());
            String repoName = rootProject.getArtifactId();
            try (Repository repository = GitUtil.getGitRepository(getProject())) {
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

    // =================================

    private void addGitServiceUrl(Map<String, String> annotations, String repoName, String gitCommitId) {
        String username = getGitUserName();
        // this requires online access to kubernetes so we should silently fail if no connection
        String gogsUrl = getExternalServiceURL(getConfig(Config.gitService), "http");
        String rootGitUrl = URLUtils.pathJoin(gogsUrl, username, repoName);
        rootGitUrl = URLUtils.pathJoin(rootGitUrl, "commit", gitCommitId);
        if (Strings.isNotBlank(rootGitUrl)) {
            annotations.put(Annotations.Builds.GIT_URL, rootGitUrl);
        }
    }

    private void addJenkinsServiceUrl(Map<String, String> annotations, String repoName) {
        String buildId = Systems.getEnvVarOrSystemProperty("BUILD_ID");
        if (buildId != null) {
            annotations.put(Annotations.Builds.BUILD_ID, buildId);
            String serviceUrl = getExternalServiceURL(getConfig(Config.jenkinsService), "http");
            if (serviceUrl != null) {
                String jobUrl = URLUtils.pathJoin(serviceUrl, "/job", repoName);
                jobUrl = URLUtils.pathJoin(jobUrl, buildId);
                annotations.put(Annotations.Builds.BUILD_URL, jobUrl);
            }
        } else {
            log.debug("No Jenkins annotation as no BUILD_ID could be found");
        }
    }

    private String getGitUserName() {
        String username;
        String userEnvVar = getConfig(Config.gitUserEnvVar);
        username = Systems.getEnvVarOrSystemProperty(userEnvVar);
        if (Strings.isNullOrBlank(username)) {
            username = "gogsadmin";
        }
        return username;
    }

}
