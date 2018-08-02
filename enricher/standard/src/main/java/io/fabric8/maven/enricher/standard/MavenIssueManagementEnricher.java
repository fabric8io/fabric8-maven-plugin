/*
 *    Copyright (c) 2016 Red Hat, Inc.
 *
 *    Red Hat licenses this file to you under the Apache License, version
 *    2.0 (the "License"); you may not use this file except in compliance
 *    with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *    implied.  See the License for the specific language governing
 *    permissions and limitations under the License.
 */

package io.fabric8.maven.enricher.standard;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.maven.core.util.kubernetes.Fabric8Annotations;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;
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
    static final String ENRICHER_NAME = "f8-maven-issue-mgmt";

    public MavenIssueManagementEnricher(EnricherContext buildContext) {
        super(buildContext, ENRICHER_NAME);
    }

    @Override
    public Map<String, String> getAnnotations(Kind kind) {
        Map<String, String> annotations = new HashMap<>();
        if (kind.isController() || kind == Kind.SERVICE) {
            MavenProject rootProject = getProject();
            if (hasIssueManagement(rootProject)) {
                IssueManagement issueManagement = rootProject.getIssueManagement();
                String system = issueManagement.getSystem();
                String url = issueManagement.getUrl();
                if (StringUtils.isNotEmpty(system) && StringUtils.isNotEmpty(url)) {
                    annotations.put(Fabric8Annotations.ISSUE_SYSTEM.value(), system);
                    annotations.put(Fabric8Annotations.ISSUE_TRACKER_URL.value(), url);
                    return annotations;
                }
            }
        }
        return annotations;
    }

    private boolean hasIssueManagement(MavenProject project) {
        return project.getIssueManagement() != null;
    }

}
