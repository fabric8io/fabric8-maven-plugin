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

import java.util.Arrays;
import java.util.Collections;
import java.util.TreeMap;

import com.google.common.io.Files;
import com.jayway.jsonpath.matchers.JsonPathMatchers;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.util.ResourceUtil;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.enricher.api.EnricherContext;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.apache.maven.project.MavenProject;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author kamesh
 * @since 08/05/17
 */
@RunWith(JMockit.class)
public class DefaultControllerEnricherTest {

    @Mocked
    private EnricherContext context;

    @Mocked
    ImageConfiguration imageConfiguration;

    @Mocked
    MavenProject project;

    @Test
    public void checkReplicaCount() throws Exception {
        enrichAndAssert(1, 3);
    }

    @Test
    public void checkDefaultReplicaCount() throws Exception {
        enrichAndAssert(1, 1);
    }

    protected void enrichAndAssert(int sizeOfObjects, int replicaCount) throws com.fasterxml.jackson.core.JsonProcessingException {
        // Setup a sample docker build configuration
        final BuildImageConfiguration buildConfig =
                new BuildImageConfiguration.Builder()
                        .ports(Arrays.asList("8080"))
                        .build();

        final TreeMap controllerConfig = new TreeMap();
        controllerConfig.put("replicaCount", String.valueOf(replicaCount));

        setupExpectations(buildConfig, controllerConfig);
        // Enrich
        DefaultControllerEnricher controllerEnricher = new DefaultControllerEnricher(context);
        KubernetesListBuilder builder = new KubernetesListBuilder();
        controllerEnricher.addMissingResources(builder);

        // Validate that the generated resource contains
        KubernetesList list = builder.build();
        assertEquals(sizeOfObjects, list.getItems().size());

        String json = ResourceUtil.toJson(list.getItems().get(0));
        assertThat(json, JsonPathMatchers.isJson());
        assertThat(json, JsonPathMatchers.hasJsonPath("$.spec.replicas", Matchers.equalTo(replicaCount)));
    }

    protected void setupExpectations(final BuildImageConfiguration buildConfig, final TreeMap controllerConfig) {
        new Expectations() {{

            project.getArtifactId();
            result = "fmp-controller-test";

            project.getBuild().getOutputDirectory();
            result = Files.createTempDir().getAbsolutePath();

            context.getProject();
            result = project;

            context.getConfig();
            result = new ProcessorConfig(null, null,
                    Collections.singletonMap("fmp-controller", controllerConfig));

            imageConfiguration.getBuildConfiguration();
            result = buildConfig;

            imageConfiguration.getName();
            result = "helloworld";

            context.getImages();
            result = Arrays.asList(imageConfiguration);
        }};
    }
}
