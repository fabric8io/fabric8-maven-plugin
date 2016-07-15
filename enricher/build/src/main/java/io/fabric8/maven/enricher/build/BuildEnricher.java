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

package io.fabric8.maven.enricher.build;

import io.fabric8.kubernetes.api.Annotations;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.ServiceNames;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.maven.core.config.ResourceConfiguration;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.enricher.api.*;
import io.fabric8.utils.GitHelpers;
import io.fabric8.utils.Strings;
import io.fabric8.utils.Systems;
import io.fabric8.utils.URLUtils;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enricher for adding build metadata:
 *
 * <ul>
 *   <li>Git Branch</li>
 *   <li>Git Commit ID</li>
 *   <li></li>
 * </ul>
 *
 * @since 01/05/16
 */
public class BuildEnricher extends AbstractLiveEnricher {

    // Available configuration keys
    private enum Config implements Configs.Key {
        cdBuild        {{  d = "true"; }},
        gitService     {{  d = ServiceNames.GOGS; }},
        jenkinsService {{  d = ServiceNames.JENKINS; }},
        useEnvVar      {{  d = "JENKINS_GOGS_USER"; }};

        public String def() { return d; } protected String d;
    }

    public BuildEnricher(EnricherContext buildContext) {
        super(buildContext, "build");
    }

    @Override
    public Map<String, String> getAnnotations(Kind kind) {
        Map<String, String> annotations = new HashMap<>();
        if (kind.isDeployOrReplicaKind() || kind.isService()) {
            MavenProject rootProject = MavenUtil.getRootProject(getProject());

            Repository repository = getGitRepository(rootProject);
            if (repository == null) {
                // Couldn't fetch any local git information, so git enrichment
                return null;
            }

            String repoName = rootProject.getArtifactId();
            addGitBranch(annotations, repository);
            String gitCommitId = getAndAddGitCommitId(annotations, repository);

            if (isOnline()) {
                addGitUrl(annotations, repoName, gitCommitId);
                addJenkinsUrl(annotations, repoName);
            } else {
                getLog().info(
                    "Not looking for Kubernetes services " +
                    ServiceNames.GOGS + " and " + ServiceNames.JENKINS +
                    " because runnig in offline mode");
            };
        }
        return annotations;
    }

    // =================================

    private void addGitUrl(Map<String, String> annotations, String repoName, String gitCommitId) {
        String username = getUserName();
        if (gitCommitId != null) {
            // this requires online access to kubernetes so we should silently fail if no connection
            String gogsUrl = getExternalServiceURL(getConfig(Config.gitService), "http");
            String rootGitUrl = URLUtils.pathJoin(gogsUrl, username, repoName);
            rootGitUrl = URLUtils.pathJoin(rootGitUrl, "commit", gitCommitId);
            if (Strings.isNotBlank(rootGitUrl)) {
                annotations.put(Annotations.Builds.GIT_URL, rootGitUrl);
            }
        }
    }

    private void addJenkinsUrl(Map<String,String> annotations, String repoName) {
        String buildId = Systems.getEnvVarOrSystemProperty("BUILD_ID");

        if (Strings.isNullOrBlank(buildId)) {
            warnCD("Cannot find $BUILD_ID so must not be inside a Jenkins build");
        } else {
            annotations.put(Annotations.Builds.BUILD_ID, buildId);
            String serviceUrl = getExternalServiceURL(getConfig(Config.jenkinsService), "http");
            if (serviceUrl != null) {
                String jobUrl = URLUtils.pathJoin(serviceUrl, "/job", repoName);
                jobUrl = URLUtils.pathJoin(jobUrl, buildId);
                annotations.put(Annotations.Builds.BUILD_URL, jobUrl);
            }
        }
    }

    private String getUserName() {
        String username;
        String userEnvVar = getConfig(Config.useEnvVar);
        username = Systems.getEnvVarOrSystemProperty(userEnvVar);
        if (Strings.isNullOrBlank(username)) {
            username = "gogsadmin";
        }
        return username;
    }

    private void addGitBranch(Map<String, String> annotations, Repository repository) {
        try {
            String branch = repository.getBranch();
            if (Strings.isNotBlank(branch)) {
                annotations.put(Annotations.Builds.GIT_BRANCH, branch);
            }
        } catch (IOException e) {
            warnCD("Failed to find git branch: " + e, e);
        } finally {
            if (repository != null) {
                repository.close();
            }
        }
    }

    private String getAndAddGitCommitId(Map<String, String> annotations, Repository repository) {
        try {
            if (repository != null) {
                getLog().info("Looking at repo with directory " + repository.getDirectory());
                Iterable<RevCommit> logs = new Git(repository).log().call();
                for (RevCommit rev : logs) {
                    String gitCommitId = rev.getName();
                    annotations.put(Annotations.Builds.GIT_COMMIT, gitCommitId);
                    return gitCommitId;
                }
                warnCD("Cannot find git commit SHA as no commits could be found");
            } else {
                warnCD("Cannot find git commit SHA as no git repository could be found");
            }
        } catch (Exception e) {
            warnCD("Failed to find git commit id: " + e, e);
        } finally {
            if (repository != null) {
                try {
                    repository.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        return null;
    }


    // ====================================================================================================

    private File getBasedir(MavenProject rootProject) {
        File basedir = rootProject.getBasedir();
        if (basedir == null) {
            basedir = getProject().getBasedir();
        }
        if (basedir == null) {
            basedir = new File(System.getProperty("basedir", "."));
        }
        return basedir;
    }

    protected Repository getGitRepository(MavenProject rootProject) {
        File basedir = getBasedir(rootProject);
        try {
            File gitFolder = GitHelpers.findGitFolder(basedir);
            if (gitFolder == null) {
                warnCD("Could not find .git folder based on the current basedir of " + basedir);
                return null;
            }
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Repository repository = builder
                    .readEnvironment()
                    .setGitDir(gitFolder)
                    .build();
            if (repository == null) {
                warnCD("No .git/config file could be found so cannot annotate kubernetes resources with git commit SHA and branch");
            }
            return repository;
        } catch (Exception e) {
            warnCD("Failed to initialise Git Repository: " + e, e);
            return null;
        }
    }

    protected void warnCD(String message) {
        if (isInCDBuild()) {
            getLog().warn(message);
        } else {
            getLog().debug(message);
        }
    }

    /**
     * Returns true if the current build is being run inside a CI / CD build in which case
     * lets warn if we cannot detect things like the GIT commit or Jenkins build server URL
     */
    protected boolean isInCDBuild() {
        return Configs.asBoolean(getConfig(Config.cdBuild));
    }

    protected void warnCD(String message, Throwable exception) {
        if (isInCDBuild()) {
            getLog().warn(message, exception);
        } else {
            getLog().debug(message, exception);
        }
    }


}
