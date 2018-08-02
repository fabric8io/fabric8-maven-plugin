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

package io.fabric8.maven.core.util;

import java.io.File;
import java.io.IOException;

import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/**
 * @author roland
 * @since 28/07/16
 */
public class GitUtil {


    public static Repository getGitRepository(MavenProject project) throws IOException {
        MavenProject rootProject = MavenUtil.getRootProject(project);
        File baseDir = rootProject.getBasedir();
        if (baseDir == null) {
            baseDir = project.getBasedir();
        }
        if (baseDir == null) {
            // TODO: Why is this check needed ?
            baseDir = new File(System.getProperty("basedir", "."));
        }
        File gitFolder = findGitFolder(baseDir);
        if (gitFolder == null) {
            // No git repository found
            return null;
        }
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repository = builder
            .readEnvironment()
            .setGitDir(gitFolder)
            .build();
        return repository;
    }

    public static File findGitFolder(File basedir) {
        File gitDir = new File(basedir, ".git");
        if (gitDir.exists() && gitDir.isDirectory()) {
            return gitDir;
        }
        File parent = basedir.getParentFile();
        if (parent != null) {
            return findGitFolder(parent);
        }
        return null;
    }

    public static String getGitCommitId(Repository repository) throws GitAPIException {
        try {
            if (repository != null) {
                Iterable<RevCommit> logs = new Git(repository).log().call();
                for (RevCommit rev : logs) {
                    return rev.getName();
                }
            }
        } finally {

        }
        return null;
    }
}
