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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.maven.core.util.GitUtil;
import io.fabric8.maven.core.util.kubernetes.Fabric8Annotations;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import io.fabric8.maven.enricher.api.Kind;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

/**
 * Enricher for adding build metadata:
 *
 * <ul>
 *   <li>Git Branch</li>
 *   <li>Git Commit ID</li>
 * </ul>
 *
 * @since 01/05/16
 */
public class GitEnricher extends BaseEnricher {

    private String GIT_REMOTE = "fabric8.remoteName";

    public GitEnricher(MavenEnricherContext buildContext) {
        super(buildContext, "fmp-git");
    }

    @Override
    public Map<String, String> getAnnotations(Kind kind) {
        final Map<String, String> annotations = new HashMap<>();
        if (GitUtil.findGitFolder(getContext().getProjectDirectory()) != null) {
            Repository repository = null;
            try {
                if (kind.isController() || kind == Kind.SERVICE) {
                    // Git annotations (if git is used as SCM)
                    repository = GitUtil.getGitRepository(getContext().getProjectDirectory());
                    if (repository != null) {
                        String branch = repository.getBranch();
                        if (branch != null) {
                            annotations.put(Fabric8Annotations.GIT_BRANCH.value(), branch);
                        }
                        String id = GitUtil.getGitCommitId(repository);
                        if (id != null) {
                            annotations.put(Fabric8Annotations.GIT_COMMIT.value(), id);
                        }

                        String gitRemote = getContext().getConfiguration().getProperties().getProperty(GIT_REMOTE);
                        gitRemote = gitRemote == null? "origin" : gitRemote;
                        String gitRemoteUrl = repository.getConfig().getString("remote", gitRemote, "url");
                        if (gitRemoteUrl != null) {
                            annotations.put(Fabric8Annotations.GIT_URL.value(), gitRemoteUrl);
                        } else {
                            log.warn("Could not detect any git remote");
                        }
                    }
                }
                return annotations;
            } catch (IOException | GitAPIException e) {
                log.error("Cannot extract Git information for adding to annotations: " + e, e);
                return null;
            } finally {
                if (repository != null) {
                    try {
                        repository.close();
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        }

        return annotations;
    }
}

