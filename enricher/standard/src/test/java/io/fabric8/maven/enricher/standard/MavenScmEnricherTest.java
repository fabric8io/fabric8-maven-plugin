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

import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
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
public class MavenScmEnricherTest {

    @Mocked
    private EnricherContext context;

    @Test
    public void testMavenScmAll() {

        final MavenProject project = new MavenProject();
        final Scm scm = new Scm();
        scm.setConnection("scm:git:git://github.com/fabric8io/fabric8-maven-plugin.git");
        scm.setDeveloperConnection("scm:git:git://github.com/fabric8io/fabric8-maven-plugin.git");
        scm.setTag("HEAD");
        scm.setUrl("git://github.com/fabric8io/fabric8-maven-plugin.git");
        project.setScm(scm);
        // Setup mock behaviour
        new Expectations() {
            {
                {
                    context.getProject();
                    result = project;
                }
            }
        };

        MavenScmEnricher mavenScmEnricher = new MavenScmEnricher(context);

        Map<String, String> scmAnnotations = mavenScmEnricher.getAnnotations(Kind.DEPLOYMENT_CONFIG);
        assertNotNull(scmAnnotations);

        Assert.assertEquals(4, scmAnnotations.size());
        assertEquals("scm:git:git://github.com/fabric8io/fabric8-maven-plugin.git",
                scmAnnotations.get(MavenScmEnricher.SCM_CONNECTION));
        assertEquals("scm:git:git://github.com/fabric8io/fabric8-maven-plugin.git",
                scmAnnotations.get(MavenScmEnricher.SCM_DEVELOPER_CONNECTION));
        assertEquals("HEAD",
                scmAnnotations.get(MavenScmEnricher.SCM_TAG));
        assertEquals("git://github.com/fabric8io/fabric8-maven-plugin.git",
                scmAnnotations.get(MavenScmEnricher.SCM_URL));

    }

    @Test
    public void testMavenScmOnlyConnection() {

        final MavenProject project = new MavenProject();
        final Scm scm = new Scm();
        scm.setConnection("scm:git:git://github.com/fabric8io/fabric8-maven-plugin.git");
        project.setScm(scm);
        // Setup mock behaviour
        new Expectations() {
            {
                {
                    context.getProject();
                    result = project;
                }
            }
        };

        MavenScmEnricher mavenScmEnricher = new MavenScmEnricher(context);

        Map<String, String> scmAnnotations = mavenScmEnricher.getAnnotations(Kind.DEPLOYMENT_CONFIG);
        assertNotNull(scmAnnotations);

        Assert.assertEquals(2, scmAnnotations.size());
        assertEquals("scm:git:git://github.com/fabric8io/fabric8-maven-plugin.git",
                scmAnnotations.get(MavenScmEnricher.SCM_CONNECTION));
        assertEquals("HEAD",
                scmAnnotations.get(MavenScmEnricher.SCM_TAG));

    }

    @Test
    public void testMavenScmOnlyDevConnection() {

        final MavenProject project = new MavenProject();
        final Scm scm = new Scm();
        scm.setUrl("git://github.com/fabric8io/fabric8-maven-plugin.git");
        project.setScm(scm);
        // Setup mock behaviour
        new Expectations() {
            {
                {
                    context.getProject();
                    result = project;
                }
            }
        };

        MavenScmEnricher mavenScmEnricher = new MavenScmEnricher(context);

        Map<String, String> scmAnnotations = mavenScmEnricher.getAnnotations(Kind.DEPLOYMENT_CONFIG);
        assertNotNull(scmAnnotations);

        Assert.assertEquals(2, scmAnnotations.size());
        assertEquals("git://github.com/fabric8io/fabric8-maven-plugin.git",
                scmAnnotations.get(MavenScmEnricher.SCM_URL));
        assertEquals("HEAD",
                scmAnnotations.get(MavenScmEnricher.SCM_TAG));

    }

    @Test
    public void testMavenScmOnlyUrl() {

        final MavenProject project = new MavenProject();
        final Scm scm = new Scm();
        scm.setDeveloperConnection("scm:git:git://github.com/fabric8io/fabric8-maven-plugin.git");
        project.setScm(scm);
        // Setup mock behaviour
        new Expectations() {
            {
                {
                    context.getProject();
                    result = project;
                }
            }
        };

        MavenScmEnricher mavenScmEnricher = new MavenScmEnricher(context);

        Map<String, String> scmAnnotations = mavenScmEnricher.getAnnotations(Kind.DEPLOYMENT_CONFIG);
        assertNotNull(scmAnnotations);

        Assert.assertEquals(2, scmAnnotations.size());
        assertEquals("scm:git:git://github.com/fabric8io/fabric8-maven-plugin.git",
                scmAnnotations.get(MavenScmEnricher.SCM_DEVELOPER_CONNECTION));
        assertEquals("HEAD",
                scmAnnotations.get(MavenScmEnricher.SCM_TAG));

    }

    @Test
    public void testMavenNoScm() {

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

        MavenScmEnricher mavenScmEnricher = new MavenScmEnricher(context);

        Map<String, String> scmAnnotations = mavenScmEnricher.getAnnotations(Kind.DEPLOYMENT_CONFIG);
        assertTrue(scmAnnotations.isEmpty());

    }

}
