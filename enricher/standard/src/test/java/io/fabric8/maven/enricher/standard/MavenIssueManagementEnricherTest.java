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

import java.util.Map;

import io.fabric8.maven.core.util.kubernetes.Fabric8Annotations;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

/**
 * @author kameshs
 */
@RunWith(JMockit.class)
public class MavenIssueManagementEnricherTest {

    @Mocked
    private EnricherContext context;

    @Test
    public void testMavenIssueManagementAll() {

        final MavenProject project = new MavenProject();
        final IssueManagement issueManagement = new IssueManagement();
        issueManagement.setSystem("GitHub");
        issueManagement.setUrl("https://github.com/fabric8io/vertx-maven-plugin/issues/");
        project.setIssueManagement(issueManagement);
        // Setup mock behaviour
        new Expectations() {
            {
                {
                    context.getProject();
                    result = project;
                }
            }
        };

        MavenIssueManagementEnricher enricher = new MavenIssueManagementEnricher(context);
        Map<String, String> scmAnnotations = enricher.getAnnotations(Kind.DEPLOYMENT_CONFIG);
        assertNotNull(scmAnnotations);
        Assert.assertEquals(2, scmAnnotations.size());
        assertEquals("GitHub",
                scmAnnotations.get(Fabric8Annotations.ISSUE_SYSTEM.value()));
        assertEquals("https://github.com/fabric8io/vertx-maven-plugin/issues/",
                scmAnnotations.get(Fabric8Annotations.ISSUE_TRACKER_URL.value()));
    }

    @Test
    public void testMavenIssueManagementOnlySystem() {

        final MavenProject project = new MavenProject();
        final IssueManagement issueManagement = new IssueManagement();
        issueManagement.setSystem("GitHub");
        project.setIssueManagement(issueManagement);
        // Setup mock behaviour
        new Expectations() {
            {
                {
                    context.getProject();
                    result = project;
                }
            }
        };

        MavenIssueManagementEnricher enricher = new MavenIssueManagementEnricher(context);
        Map<String, String> scmAnnotations = enricher.getAnnotations(Kind.DEPLOYMENT_CONFIG);
        assertTrue(scmAnnotations.isEmpty());
    }

    @Test
    public void testMavenIssueManagementOnlyUrl() {

        final MavenProject project = new MavenProject();
        final IssueManagement issueManagement = new IssueManagement();
        issueManagement.setUrl("https://github.com/fabric8io/fabric8-maven-plugin/issues/");
        project.setIssueManagement(issueManagement);
        // Setup mock behaviour
        new Expectations() {
            {
                {
                    context.getProject();
                    result = project;
                }
            }
        };

        MavenIssueManagementEnricher enricher = new MavenIssueManagementEnricher(context);
        Map<String, String> scmAnnotations = enricher.getAnnotations(Kind.DEPLOYMENT_CONFIG);
        assertTrue(scmAnnotations.isEmpty());
    }


    @Test
    public void testMavenNoIssueManagement() {

        final MavenProject project = new MavenProject();
        // Setup mock behaviour
        new Expectations() {
            {
                {
                    context.getProject();
                    result = project;
                }
            }
        };

        MavenIssueManagementEnricher enricher = new MavenIssueManagementEnricher(context);
        Map<String, String> scmAnnotations = enricher.getAnnotations(Kind.DEPLOYMENT_CONFIG);
        assertTrue(scmAnnotations.isEmpty());
    }


}
