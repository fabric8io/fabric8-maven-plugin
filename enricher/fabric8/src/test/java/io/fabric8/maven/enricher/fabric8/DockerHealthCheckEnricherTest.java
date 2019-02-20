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
package io.fabric8.maven.enricher.fabric8;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jayway.jsonpath.matchers.JsonPathMatchers;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.model.Configuration;
import io.fabric8.maven.core.util.ResourceUtil;
import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.HealthCheckConfiguration;
import io.fabric8.maven.docker.config.HealthCheckMode;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import mockit.Expectations;
import mockit.Mocked;
import org.assertj.core.api.ListAssert;
import org.hamcrest.Matchers;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author nicola
 */
public class DockerHealthCheckEnricherTest {

    @Mocked
    private MavenEnricherContext context;

    @Test
    public void testEnrichFromSingleImage() throws Exception {
        // Setup mock behaviour
        new Expectations() {{
            List<ImageConfiguration> images =  Arrays.asList(new ImageConfiguration.Builder()
                            .alias("myImage")
                            .buildConfig(new BuildImageConfiguration.Builder()
                                    .healthCheck(new HealthCheckConfiguration.Builder()
                                            .mode(HealthCheckMode.cmd)
                                            .cmd(new Arguments("/bin/check"))
                                            .timeout("1s")
                                            .interval("1h1s")
                                            .retries(3)
                                            .build())
                                    .build())
                            .build(),
                                                   new ImageConfiguration.Builder()
                            .alias("myImage2")
                            .buildConfig(new BuildImageConfiguration.Builder()
                                    .healthCheck(new HealthCheckConfiguration.Builder()
                                            .mode(HealthCheckMode.cmd)
                                            .cmd(new Arguments("/xxx/check"))
                                            .timeout("3s")
                                            .interval("3h1s")
                                            .retries(9)
                                            .build())
                                    .build())
                            .build());
            context.getConfiguration();
            result = new Configuration.Builder().images(images).build();
        }};

        KubernetesListBuilder builder = createDeployment("myImage");

        DockerHealthCheckEnricher enricher = new DockerHealthCheckEnricher(context);
        enricher.addMissingResources(PlatformMode.kubernetes, builder);

        KubernetesList list = builder.build();
        assertEquals(1, list.getItems().size());
        assertHealthCheckMatching(builder.build().getItems().get(0), "livenessProbe", "/bin/check", 1, 3601, 3);
        assertHealthCheckMatching(builder.build().getItems().get(0), "readinessProbe", "/bin/check", 1, 3601, 3);
    }

    @Test
    public void testEnrichFromDoubleImage() throws Exception {
        // Setup mock behaviour
        new Expectations() {{
            List<ImageConfiguration> images = Arrays.asList(new ImageConfiguration.Builder()
                            .alias("myImage")
                            .buildConfig(new BuildImageConfiguration.Builder()
                                    .healthCheck(new HealthCheckConfiguration.Builder()
                                            .mode(HealthCheckMode.cmd)
                                            .cmd(new Arguments("/bin/check"))
                                            .timeout("1s")
                                            .interval("1h1s")
                                            .retries(3)
                                            .build())
                                    .build())
                            .build(),
                    new ImageConfiguration.Builder()
                            .alias("myImage2")
                            .buildConfig(new BuildImageConfiguration.Builder()
                                    .healthCheck(new HealthCheckConfiguration.Builder()
                                            .mode(HealthCheckMode.cmd)
                                            .cmd(new Arguments("/xxx/check"))
                                            .timeout("3s")
                                            .interval("3h1s")
                                            .retries(9)
                                            .build())
                                    .build())
                            .build());
            context.getConfiguration();
            result = new Configuration.Builder().images(images).build();
        }};

        KubernetesListBuilder builder = addDeployment(createDeployment("myImage"), "myImage2");

        DockerHealthCheckEnricher enricher = new DockerHealthCheckEnricher(context);
        enricher.addMissingResources(PlatformMode.kubernetes, builder);

        KubernetesList list = builder.build();
        assertEquals(2, list.getItems().size());
        assertHealthCheckMatching(builder.build().getItems().get(0), "livenessProbe", "/bin/check", 1, 3601, 3);
        assertHealthCheckMatching(builder.build().getItems().get(0), "readinessProbe", "/bin/check", 1, 3601, 3);
        assertHealthCheckMatching(builder.build().getItems().get(1), "livenessProbe", "/xxx/check", 3, 10801, 9);
        assertHealthCheckMatching(builder.build().getItems().get(1), "readinessProbe", "/xxx/check", 3, 10801, 9);
    }

    @Test
    public void testInvalidHealthCheck() throws Exception {
        // Setup mock behaviour
        new Expectations() {{
            List<ImageConfiguration> images = Arrays.asList(new ImageConfiguration.Builder()
                    .alias("myImage")
                    .buildConfig(new BuildImageConfiguration.Builder()
                            .healthCheck(new HealthCheckConfiguration.Builder()
                                    .mode(HealthCheckMode.none)
                                    .build())
                            .build())
                    .build());
            context.getConfiguration();
            result = new Configuration.Builder().images(images).build();
        }};

        KubernetesListBuilder builder = createDeployment("myImage");

        DockerHealthCheckEnricher enricher = new DockerHealthCheckEnricher(context);
        enricher.addMissingResources(PlatformMode.kubernetes, builder);

        KubernetesList list = builder.build();
        assertEquals(1, list.getItems().size());
        assertNoProbes(list.getItems().get(0));
    }

    @Test
    public void testUnmatchingHealthCheck() throws Exception {
        // Setup mock behaviour
        new Expectations() {{
            List<ImageConfiguration> images = Arrays.asList(new ImageConfiguration.Builder()
                    .alias("myImage")
                    .buildConfig(new BuildImageConfiguration.Builder()
                            .healthCheck(new HealthCheckConfiguration.Builder()
                                    .mode(HealthCheckMode.cmd)
                                    .cmd(new Arguments("/bin/check"))
                                    .timeout("1s")
                                    .interval("1h1s")
                                    .retries(3)
                                    .build())
                            .build())
                    .build());
            context.getConfiguration();
            result = new Configuration.Builder().images(images).build();
        }};

        KubernetesListBuilder builder = createDeployment("myUnmatchingImage");

        DockerHealthCheckEnricher enricher = new DockerHealthCheckEnricher(context);
        enricher.addMissingResources(PlatformMode.kubernetes, builder);

        KubernetesList list = builder.build();
        assertEquals(1, list.getItems().size());
        assertNoProbes(list.getItems().get(0));
    }

    private KubernetesListBuilder createDeployment(String name) {
        return addDeployment(new KubernetesListBuilder(), name);
    }

    private KubernetesListBuilder addDeployment(KubernetesListBuilder list, String name) {
        return list.addNewDeploymentItem()
                .withNewMetadata()
                .withName(name)
                .endMetadata()
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()
                .addNewContainer()
                .withName(name)
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .endDeploymentItem();
    }

    private void assertNoProbes(HasMetadata object) throws JsonProcessingException {
        String json = ResourceUtil.toJson(object);
        assertThat(json, JsonPathMatchers.isJson());
        assertThat(json, JsonPathMatchers.hasJsonPath("$.spec.template.spec.containers[0]", Matchers.not(Matchers.hasKey("livenessProbe"))));
        assertThat(json, JsonPathMatchers.hasJsonPath("$.spec.template.spec.containers[0]", Matchers.not(Matchers.hasKey("readinessProbe"))));
    }

    private void assertHealthCheckMatching(HasMetadata object, String type, String command, Integer timeoutSeconds, Integer periodSeconds, Integer failureThreshold) throws JsonProcessingException {
        String json = ResourceUtil.toJson(object);
        assertThat(json, JsonPathMatchers.isJson());
        assertThat(json, JsonPathMatchers.hasJsonPath("$.spec.template.spec.containers[0]", Matchers.hasKey(type)));

        if (command != null) {
            assertThat(json, JsonPathMatchers.hasJsonPath("$.spec.template.spec.containers[0]." + type + ".exec.command[0]", Matchers.equalTo(command)));
        }
        if (timeoutSeconds != null) {
            assertThat(json, JsonPathMatchers.hasJsonPath("$.spec.template.spec.containers[0]." + type + ".timeoutSeconds", Matchers.equalTo(timeoutSeconds)));
        }
        if (periodSeconds != null) {
            assertThat(json, JsonPathMatchers.hasJsonPath("$.spec.template.spec.containers[0]." + type + ".periodSeconds", Matchers.equalTo(periodSeconds)));
        }
        if (failureThreshold != null) {
            assertThat(json, JsonPathMatchers.hasJsonPath("$.spec.template.spec.containers[0]." + type + ".failureThreshold", Matchers.equalTo(failureThreshold)));
        }
    }

}
