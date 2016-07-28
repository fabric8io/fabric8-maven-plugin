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
import io.fabric8.maven.core.util.GitUtil;
import io.fabric8.maven.enricher.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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

    public GitEnricher(EnricherContext buildContext) {
        super(buildContext, "f8-git");
    }

    @Override
    public Map<String, String> getAnnotations(Kind kind) {
        Map<String, String> annotations = new HashMap<>();
        Repository repository = null;
        try {
            if (kind.isDeployOrReplicaKind() || kind.isService()) {
                // Git annotations (if git is used as SCM)
                repository = GitUtil.getGitRepository(getProject());
                if (repository != null) {
                    String result;
                    String branch = repository.getBranch();
                    if (branch != null) {
                        annotations.put(Annotations.Builds.GIT_BRANCH, branch);
                    }
                    String id = GitUtil.getGitCommitId(repository);
                    if (id != null) {
                        annotations.put(Annotations.Builds.GIT_COMMIT, id);
                    }
                } else {
                    log.warn("No .git/config file could be found so cannot annotate kubernetes resources with git commit SHA and branch");
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
}

