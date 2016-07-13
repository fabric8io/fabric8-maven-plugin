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

package io.fabric8.maven.enricher.git;

import io.fabric8.kubernetes.api.Annotations;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.ServiceNames;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;
import io.fabric8.maven.enricher.api.Kinds;
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
 * Enricher for adding git metadata to builds
 *
 * @since 01/05/16
 */
public class GitEnricher extends BaseEnricher {

    // Available configuration keys
    private enum Config implements Configs.Key {
        cdBuild {{
            d = "true";
        }},
        useEnvVar {{
            d = "JENKINS_GOGS_USER";
        }};

        public String def() {
            return d;
        }

        protected String d;
    }

    public GitEnricher(EnricherContext buildContext) {
        super(buildContext, "git");

    }


    @Override
    public Map<String, String> getAnnotations(Kind kind) {
        Map<String, String> answer = new HashMap<>();
        if (Kinds.isDeployOrReplicaKind(kind) || kind == Kind.SERVICE) {
            MavenProject project = getProject();
            if (project == null) {
                return null;
            }
            MavenProject rootProject = MavenUtil.getRootProject(project);
            File basedir = rootProject.getBasedir();
            if (basedir == null) {
                basedir = project.getBasedir();
            }
            if (basedir == null) {
                basedir = new File(System.getProperty("basedir", "."));
            }
            String repoName = rootProject.getArtifactId();
            KubernetesClient kubernetes = getKubernetes();

            String userEnvVar = getConfig(Config.useEnvVar);
            String username = Systems.getEnvVarOrSystemProperty(userEnvVar);
            if (Strings.isNullOrBlank(username)) {
                username = "gogsadmin";
            }

            if (Strings.isNotBlank(repoName) && Strings.isNotBlank(username)) {
                Repository repository = getGitRepository(basedir);
                String gitCommitId = null;
                try {
                    if (repository != null) {
                        String branch = repository.getBranch();
                        if (Strings.isNotBlank(branch)) {
                            answer.put(Annotations.Builds.GIT_BRANCH, branch);
                        }
                        gitCommitId = getGitCommitId(repository);
                    }
                } catch (IOException e) {
                    warnIfInCDBuild("Failed to find git branch: " + e, e);
                } finally {
                    if (repository != null) {
                        repository.close();
                    }
                }

                if (isOffline()) {
                    getLog().info("Not looking for kubernetes service " + ServiceNames.GOGS + " as in offline mode");
                } else {
                    try {
                        if (Strings.isNotBlank(gitCommitId)) {
                            answer.put(Annotations.Builds.GIT_COMMIT, gitCommitId);

                            // this requires online access to kubernetes so we should silently fail if no connection
                            String gogsUrl = KubernetesHelper.getServiceURL(kubernetes, ServiceNames.GOGS, kubernetes.getNamespace(), "http", true);
                            String rootGitUrl = URLUtils.pathJoin(gogsUrl, username, repoName);
                            rootGitUrl = URLUtils.pathJoin(rootGitUrl, "commit", gitCommitId);

                            if (Strings.isNotBlank(rootGitUrl)) {
                                answer.put(Annotations.Builds.GIT_URL, rootGitUrl);
                            }
                        }
                    } catch (Throwable e) {
                        Throwable cause = e;

                        boolean notFound = false;
                        boolean connectError = false;
                        Iterable<Throwable> it = createExceptionIterable(e);
                        for (Throwable t : it) {
                            notFound = t instanceof IllegalArgumentException || t.getMessage() != null && t.getMessage().startsWith("No kubernetes service could be found for name");
                            connectError = t instanceof ConnectException || "No route to host".equals(t.getMessage());
                            if (connectError) {
                                cause = t;
                                break;
                            }
                        }

                        if (connectError) {
                            warnIfInCDBuild("Cannot connect to Kubernetes to find gogs service URL: " + cause.getMessage());
                        } else if (notFound) {
                            // the message from the exception is good as-is
                            warnIfInCDBuild(cause.getMessage());
                        } else {
                            warnIfInCDBuild("Cannot find gogs service URL: " + cause, cause);
                        }
                    }
                }
            } else {
                warnIfInCDBuild("Cannot auto-default GIT_URL as there is no environment variable `" + userEnvVar + "` defined so we can't guess the Gogs build URL");
            }

            if (Strings.isNotBlank(repoName)) {
                String buildId = Systems.getEnvVarOrSystemProperty("BUILD_ID");
                if (Strings.isNullOrBlank(buildId)) {
                    warnIfInCDBuild("Cannot find $BUILD_ID so must not be inside a Jenkins build");
                } else {
                    answer.put(Annotations.Builds.BUILD_ID, buildId);
                    if (isOffline()) {
                        getLog().info("Not looking for kubernetes service " + ServiceNames.JENKINS + " as in offline mode");
                    } else {
                        String jobUrl = null;
                        try {
                            // this requires online access to kubernetes so we should silently fail if no connection
                            String jenkinsUrl = KubernetesHelper.getServiceURL(kubernetes, ServiceNames.JENKINS, kubernetes.getNamespace(), "http", true);
                            jobUrl = URLUtils.pathJoin(jenkinsUrl, "/job", repoName);
                        } catch (Throwable e) {
                            Throwable cause = e;

                            boolean notFound = false;
                            boolean connectError = false;
                            Iterable<Throwable> it = createExceptionIterable(e);
                            for (Throwable t : it) {
                                connectError = t instanceof ConnectException || "No route to host".equals(t.getMessage());
                                notFound = t instanceof IllegalArgumentException || t.getMessage() != null && t.getMessage().startsWith("No kubernetes service could be found for name");
                                if (connectError || notFound) {
                                    cause = t;
                                    break;
                                }
                            }

                            if (connectError) {
                                warnIfInCDBuild("Cannot connect to Kubernetes to find jenkins service URL: " + cause.getMessage());
                            } else if (notFound) {
                                // the message from the exception is good as-is
                                warnIfInCDBuild(cause.getMessage());
                            } else {
                                warnIfInCDBuild("Cannot find jenkins service URL: " + cause, cause);
                            }
                        }
                        if (Strings.isNotBlank(jobUrl)) {
                            jobUrl = URLUtils.pathJoin(jobUrl, buildId);
                            answer.put(Annotations.Builds.BUILD_URL, jobUrl);
                        }
                    }
                }
            }
        }
        return answer;
    }


    // ====================================================================================================

    protected String getGitCommitId(Repository repository) {
        try {
            if (repository != null) {
                getLog().info("Looking at repo with directory " + repository.getDirectory());
                Iterable<RevCommit> logs = new Git(repository).log().call();
                for (RevCommit rev : logs) {
                    return rev.getName();
                }
                warnIfInCDBuild("Cannot find git commit SHA as no commits could be found");
            } else {
                warnIfInCDBuild("Cannot find git commit SHA as no git repository could be found");
            }
        } catch (Exception e) {
            warnIfInCDBuild("Failed to find git commit id. " + e, e);
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

    protected Repository getGitRepository(File basedir) {
        try {
            File gitFolder = GitHelpers.findGitFolder(basedir);
            if (gitFolder == null) {
                warnIfInCDBuild("Could not find .git folder based on the current basedir of " + basedir);
                return null;
            }
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Repository repository = builder
                    .readEnvironment()
                    .setGitDir(gitFolder)
                    .build();
            if (repository == null) {
                warnIfInCDBuild("No .git/config file could be found so cannot annotate kubernetes resources with git commit SHA and branch");
            }
            return repository;
        } catch (Exception e) {
            warnIfInCDBuild("Failed to initialise Git Repository: " + e, e);
            return null;
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
    protected static Iterable<Throwable> createExceptionIterable(Throwable exception) {
        List<Throwable> throwables = new ArrayList<Throwable>();

        Throwable current = exception;
        // spool to the bottom of the caused by tree
        while (current != null) {
            throwables.add(current);
            current = current.getCause();
        }
        Collections.reverse(throwables);

        return throwables;
    }


    protected void warnIfInCDBuild(String message) {
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

    protected void warnIfInCDBuild(String message, Throwable exception) {
        if (isInCDBuild()) {
            getLog().warn(message, exception);
        } else {
            getLog().debug(message, exception);
        }
    }
}
