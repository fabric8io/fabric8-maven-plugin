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
import java.util.Properties;

import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Test label generation.
 *
 * @author nicola
 */
@RunWith(JMockit.class)
public class MavenProjectEnricherTest {

    @Mocked
    private EnricherContext context;

    @Mocked
    private MavenProject mavenProject;

    @Before
    public void setupExpectations() {
        new Expectations() {{
            context.getProject();
            result = mavenProject;

            mavenProject.getGroupId();
            result = "groupId";
            mavenProject.getArtifactId();
            result = "artifactId";
            mavenProject.getVersion();
            result = "version";
        }};
    }

    @Test
    public void testGeneratedResources() {
        ProjectEnricher projectEnricher = new ProjectEnricher(context);

        KubernetesListBuilder builder = createListWithDeploymentConfig();
        projectEnricher.adapt(builder);
        KubernetesList list = builder.build();

        Map<String, String> labels = list.getItems().get(0).getMetadata().getLabels();

        assertNotNull(labels);
        assertEquals("groupId", labels.get("group"));
        assertEquals("artifactId", labels.get("app"));
        assertEquals("version", labels.get("version"));
        assertNull(labels.get("project"));

        Map<String, String> selectors = projectEnricher.getSelector (Kind.DEPLOYMENT_CONFIG);
        assertEquals("groupId", selectors.get("group"));
        assertEquals("artifactId", selectors.get("app"));
        assertNull(selectors.get("version"));
        assertNull(selectors.get("project"));
    }

    @Test
    public void testOldStyleGeneratedResources() {

        final Properties properties = new Properties();
        properties.setProperty("fabric8.enricher.fmp-project.useProjectLabel", "true");
        new Expectations() {{
            mavenProject.getProperties();
            result = properties;
        }};

        ProjectEnricher projectEnricher = new ProjectEnricher(context);

        KubernetesListBuilder builder = createListWithDeploymentConfig();
        projectEnricher.adapt(builder);
        KubernetesList list = builder.build();

        Map<String, String> labels = list.getItems().get(0).getMetadata().getLabels();

        assertNotNull(labels);
        assertEquals("groupId", labels.get("group"));
        assertEquals("artifactId", labels.get("project"));
        assertEquals("version", labels.get("version"));
        assertNull(labels.get("app"));

        Map<String, String> selectors = projectEnricher.getSelector (Kind.DEPLOYMENT_CONFIG);
        assertEquals("groupId", selectors.get("group"));
        assertEquals("artifactId", selectors.get("project"));
        assertNull(selectors.get("version"));
        assertNull(selectors.get("app"));
    }

    private KubernetesListBuilder createListWithDeploymentConfig() {
        return new KubernetesListBuilder()
                .addNewDeploymentConfigItem()
                .withNewMetadata().endMetadata()
                .withNewSpec().endSpec()
                .endDeploymentConfigItem();
    }

}
