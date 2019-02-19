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

import java.util.Map;

import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.util.kubernetes.Fabric8Annotations;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import mockit.Expectations;
import mockit.Mocked;
import org.apache.maven.model.Scm;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

/**
 * @author kameshs
 */
public class MavenScmEnricherTest {

    @Mocked
    private MavenEnricherContext context;

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

        KubernetesListBuilder builder = new KubernetesListBuilder().withItems(new DeploymentBuilder().withNewMetadata().withName("foo").endMetadata().build());

        mavenScmEnricher.create(PlatformMode.kubernetes, builder);

        Map<String, String> scmAnnotations = builder.buildFirstItem().getMetadata().getAnnotations();
        assertNotNull(scmAnnotations);

        Assert.assertEquals(2, scmAnnotations.size());
        assertEquals("HEAD",
                scmAnnotations.get(Fabric8Annotations.SCM_TAG.value()));
        assertEquals("git://github.com/fabric8io/fabric8-maven-plugin.git",
                scmAnnotations.get(Fabric8Annotations.SCM_URL.value()));

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

        KubernetesListBuilder builder = new KubernetesListBuilder().withItems(new DeploymentBuilder().withNewMetadata().withName("foo").endMetadata().build());

        mavenScmEnricher.create(PlatformMode.kubernetes, builder);

        Map<String, String> scmAnnotations = builder.buildFirstItem().getMetadata().getAnnotations();
        assertNotNull(scmAnnotations);

        Assert.assertEquals(1, scmAnnotations.size());
        assertEquals("HEAD",
                scmAnnotations.get(Fabric8Annotations.SCM_TAG.value()));

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

        KubernetesListBuilder builder = new KubernetesListBuilder().withItems(new DeploymentBuilder().withNewMetadata().withName("foo").endMetadata().build());

        mavenScmEnricher.create(PlatformMode.kubernetes, builder);

        Map<String, String> scmAnnotations = builder.buildFirstItem().getMetadata().getAnnotations();
        assertNotNull(scmAnnotations);

        Assert.assertEquals(2, scmAnnotations.size());
        assertEquals("git://github.com/fabric8io/fabric8-maven-plugin.git",
                scmAnnotations.get(Fabric8Annotations.SCM_URL.value()));
        assertEquals("HEAD",
                scmAnnotations.get(Fabric8Annotations.SCM_TAG.value()));

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

        KubernetesListBuilder builder = new KubernetesListBuilder().withItems(new DeploymentBuilder().withNewMetadata().withName("foo").endMetadata().build());

        mavenScmEnricher.create(PlatformMode.kubernetes, builder);

        Map<String, String> scmAnnotations = builder.buildFirstItem().getMetadata().getAnnotations();
        assertNotNull(scmAnnotations);

        Assert.assertEquals(1, scmAnnotations.size());
        assertEquals("HEAD",
                scmAnnotations.get(Fabric8Annotations.SCM_TAG.value()));

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

        KubernetesListBuilder builder = new KubernetesListBuilder().withItems(new DeploymentBuilder().withNewMetadata().withName("foo").endMetadata().build());

        mavenScmEnricher.create(PlatformMode.kubernetes, builder);

        Map<String, String> scmAnnotations = builder.buildFirstItem().getMetadata().getAnnotations();
        assertNotNull(scmAnnotations);

    }

}
