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
import io.fabric8.kubernetes.api.ServiceNames;
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
import java.util.HashMap;
import java.util.Map;

/**
 * Enricher for adding build metadata:
 *
 * <ul>
 *   <li>Git Branch</li>
 *   <li>Git Commit ID</li>
 *   <li>Git access URL</li>
 *   <li>Jenkins build number</li>
 *   <li>Jenkins build url</li>
 * </ul>
 *
 * @since 01/05/16
 */
public class BuildEnricher extends AbstractLiveEnricher {

    // Available configuration keys
    private enum Config implements Configs.Key {
        gitService     {{  d = ServiceNames.GOGS; }},
        jenkinsService {{  d = ServiceNames.JENKINS; }},
        gitUserEnvVar  {{  d = "GIT_USER"; }};

        public String def() { return d; } protected String d;
    }

    public BuildEnricher(EnricherContext buildContext) {
        super(buildContext, "build");

        if (!isOnline()) {
            log.info("Run in cluster-offline mode");
        }
    }

    @Override
    protected boolean getDefaultOnline() {
        return isInCDBuild();
    }

    @Override
    public Map<String, String> getAnnotations(Kind kind) {
        Map<String, String> annotations = new HashMap<>();
        if (kind.isDeployOrReplicaKind() || kind.isService()) {
            MavenProject rootProject = MavenUtil.getRootProject(getProject());
            String repoName = rootProject.getArtifactId();

            // Git annotations (if git is used as SCM)
            Repository repository = getGitRepository(rootProject);
            if (repository != null) {
                addGitBranch(annotations, repository);
                if (isOnline()) {
                    String gitCommitId = getAndAddGitCommitId(annotations, repository);
                    if (gitCommitId != null) {
                        addGitServiceUrl(annotations, repoName, gitCommitId);
                    } else {
                        log.debug("No Git commit id found");
                    }
                }
            } else {
                log.debug("No local Git repository found");
            }

            // Jenkins annotations
            if (isOnline()) {
                addJenkinsServiceUrl(annotations, repoName);
            };
        }
        return annotations;
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

    private void addJenkinsServiceUrl(Map<String,String> annotations, String repoName) {
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

    private void addGitBranch(Map<String, String> annotations, Repository repository) {
        try {
            String branch = repository.getBranch();
            if (Strings.isNotBlank(branch)) {
                annotations.put(Annotations.Builds.GIT_BRANCH, branch);
            }
        } catch (IOException e) {
            log.warn("Failed to find git branch: " + e, e);
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
                log.warn("Cannot find git commit SHA as no commits could be found");
            } else {
                log.warn("Cannot find git commit SHA as no git repository could be found");
            }
        } catch (Exception e) {
            log.warn("Failed to find git commit id: " + e, e);
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
                // No git repository found
                return null;
            }
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Repository repository = builder
                    .readEnvironment()
                    .setGitDir(gitFolder)
                    .build();
            if (repository == null) {
                log.warn("No .git/config file could be found so cannot annotate kubernetes resources with git commit SHA and branch");
            }
            return repository;
        } catch (Exception e) {
            log.warn("Failed to initialise Git Repository: " + e, e);
            return null;
        }
    }

    /**
     * Returns true if the current build is being run inside a CI / CD build in which case
     * lets warn if we cannot detect things like the GIT commit or Jenkins build server URL
     */
    protected boolean isInCDBuild() {
        // If no online mode specified, we are supposed to be online
        // when running in an Jenkins CD.
        // TODO: Isn't there a better datum to detect a CD build ? because BUILD_CD is set
        // also when you do a 'regular' CI job ....
        return Systems.getEnvVarOrSystemProperty("BUILD_ID") != null;
    }
}
