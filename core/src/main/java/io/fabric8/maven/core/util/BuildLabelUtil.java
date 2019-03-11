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
package io.fabric8.maven.core.util;

import io.fabric8.maven.docker.config.BuildImageConfiguration;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Site;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class BuildLabelUtil {

    private static String getDocumentationUrl (MavenProject project) {
        while (project != null) {
            DistributionManagement distributionManagement = project.getDistributionManagement();
            if (distributionManagement != null) {
                Site site = distributionManagement.getSite();
                if (site != null) {
                    return site.getUrl();
                }
            }
            project = project.getParent();
        }
        return null;
    }

    public static void addSchemaLabels (BuildImageConfiguration.Builder buildBuilder, MavenProject project, PrefixedLogger log) {
        String LABEL_SCHEMA_VERSION = "1.0";
        String GIT_REMOTE = "origin";
        String docURL = getDocumentationUrl(project);
        Map<String, String> labels = new HashMap<>();

        labels.put(BuildLabelAnnotations.BUILD_DATE.value(), LocalDateTime.now().toString());
        labels.put(BuildLabelAnnotations.NAME.value(), project.getName());
        labels.put(BuildLabelAnnotations.DESCRIPTION.value(), project.getDescription());
        if (docURL != null) {
            labels.put(BuildLabelAnnotations.USAGE.value(), docURL);
        }
        if (project.getUrl() != null) {
            labels.put(BuildLabelAnnotations.URL.value(), project.getUrl());
        }
        if (project.getOrganization() != null && project.getOrganization().getName() != null) {
            labels.put(BuildLabelAnnotations.VENDOR.value(), project.getOrganization().getName());
        }
        labels.put(BuildLabelAnnotations.VERSION.value(), project.getVersion());
        labels.put(BuildLabelAnnotations.SCHEMA_VERSION.value(), LABEL_SCHEMA_VERSION);

        try {
            Repository repository = GitUtil.getGitRepository(project.getBasedir());
            String commitID = GitUtil.getGitCommitId(repository);
            labels.put(BuildLabelAnnotations.VCS_REF.value(), commitID);
            String gitRemoteUrl = repository.getConfig().getString("remote", GIT_REMOTE, "url");
            if (gitRemoteUrl != null) {
                labels.put(BuildLabelAnnotations.VCS_URL.value(), gitRemoteUrl);
            } else {
                log.warn("Could not detect any git remote");
            }
        } catch (IOException | GitAPIException | NullPointerException e) {
            log.error("Cannot extract Git information: " + e, e);
        } finally {
            buildBuilder.labels(labels);
        }
    }
}
