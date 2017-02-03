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

package io.fabric8.maven.enricher.fabric8;

import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.Scm;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

import static junit.framework.TestCase.*;

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
                scmAnnotations.get(MavenIssueManagementEnricher.ISSUE_MANAGEMENT_SYSTEM));
        assertEquals("https://github.com/fabric8io/vertx-maven-plugin/issues/",
                scmAnnotations.get(MavenIssueManagementEnricher.ISSUE_MANAGEMENT_URL));
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
        assertNull(scmAnnotations);
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
        assertNull(scmAnnotations);
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
        assertNull(scmAnnotations);
    }


}
