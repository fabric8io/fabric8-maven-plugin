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

package io.fabric8.maven.enricher.standard;

/*
 * @author rohan
 * @since 06/11/17
 */

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.core.util.KindAndName;
import io.fabric8.maven.core.util.KubernetesResourceUtil;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.enricher.api.EnricherContext;
import mockit.Expectations;
import mockit.Mocked;

import mockit.integration.junit4.JMockit;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.project.MavenProject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static io.fabric8.maven.core.util.ResourceFileType.yaml;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JMockit.class)
public class DependencyEnricherTest {

    @Mocked
    private EnricherContext context;

    @Mocked
    private ImageConfiguration imageConfiguration;

    @Mocked
    private MavenProject project;

    // Some resource files related to test case placed in resources/ directory:
    private final String overrideFragementFile = "/jenkins-kubernetes-cm.yml";
    private final String artifactFilePath = "/jenkins-4.0.41.jar";

    @Test
    public void checkDuplicatesInResource() throws Exception {
        // Generate given Resources
        KubernetesListBuilder aBuilder = createResourcesForTest();
        // Enrich
        KubernetesList aResourceList = enrichResources(aBuilder);
        // Assert
        assertTrue(aResourceList.getItems() != null);
        assertEquals(checkUniqueResources(aResourceList.getItems()), true);
    }

    private KubernetesList enrichResources(KubernetesListBuilder aBuilder) {
        DependencyEnricher enricher = new DependencyEnricher(context);
        enricher.addMissingResources(aBuilder);
        enricher.adapt(aBuilder);
        return aBuilder.build();
    }

    private KubernetesListBuilder createResourcesForTest() throws IOException {
        setupExpectations();
        List<File> resourceList = new ArrayList<>();
        resourceList.add(new File(getClass().getResource(overrideFragementFile).getFile()));

        /*
         * Our override file also contains a ConfigMap item with name jenkins, load it while
         * loading Kubernetes resources.
         */
        KubernetesListBuilder builder = KubernetesResourceUtil.readResourceFragmentsFrom(
                KubernetesResourceUtil.DEFAULT_RESOURCE_VERSIONING,
                project.getName(),
                resourceList.toArray(new File[resourceList.size()]));
        return builder;
    }

    private void setupExpectations() {
        // Setup Mock behaviour
        new Expectations() {{
            context.getProject();
            result = project;

            context.getProject().getArtifacts();
            result = getDummyArtifacts();
        }};
    }

    private Set<Artifact> getDummyArtifacts() {
        Set<Artifact> artifacts = new TreeSet<>();

        Artifact artifact = new DefaultArtifact("io.fabric8.tenant.apps", "jenkins",
                "1.0.0-SNAPSHOT", "compile", "jar", null, new DefaultArtifactHandler("jar"));
        File aFile = new File(getClass().getResource(artifactFilePath).getFile());
        artifact.setFile(aFile);
        artifacts.add(artifact);
        return artifacts;
    }

    private boolean checkUniqueResources(List<HasMetadata> resourceList) {
        Map<KindAndName, Integer> resourceMap = new HashMap<>();
        for(int index = 0; index < resourceList.size(); index++) {
            KindAndName aKey = new KindAndName(resourceList.get(index));
            if(resourceMap.containsKey(aKey))
                return false;
            resourceMap.put(aKey, index);
        }
        return true;
    }
}
