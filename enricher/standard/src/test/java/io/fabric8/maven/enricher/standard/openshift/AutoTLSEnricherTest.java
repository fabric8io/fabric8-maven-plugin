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
package io.fabric8.maven.enricher.standard.openshift;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.PodTemplate;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.model.Configuration;
import io.fabric8.maven.core.model.GroupArtifactVersion;
import io.fabric8.maven.enricher.api.Kind;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import mockit.Expectations;
import mockit.Mocked;
import org.apache.maven.project.MavenProject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AutoTLSEnricherTest {

    @Mocked
    private MavenEnricherContext context;
    @Mocked
    MavenProject project;

    // *******************************
    // Tests
    // *******************************

    private static final class SecretNameTestConfig {
        private final PlatformMode mode;
        private final String tlsSecretNameConfig;
        private final String tlsSecretName;

        private SecretNameTestConfig(PlatformMode mode, String tlsSecretNameConfig, String tlsSecretName) {
            this.mode = mode;
            this.tlsSecretNameConfig = tlsSecretNameConfig;
            this.tlsSecretName = tlsSecretName;
        }
    }

    @Test
    public void testSecretName() throws Exception {
        final SecretNameTestConfig[] data = new SecretNameTestConfig[] {
                new SecretNameTestConfig(PlatformMode.kubernetes, null, null),
                new SecretNameTestConfig(PlatformMode.openshift, null, "projectA-tls"),
                new SecretNameTestConfig(PlatformMode.openshift, "custom-secret", "custom-secret") };

        for (final SecretNameTestConfig tc : data) {
            final ProcessorConfig config = new ProcessorConfig(null, null,
                    Collections.singletonMap(AutoTLSEnricher.ENRICHER_NAME, new TreeMap(Collections
                            .singletonMap(AutoTLSEnricher.Config.tlsSecretName.name(), tc.tlsSecretNameConfig))));

            final Properties projectProps = new Properties();
            projectProps.put(PlatformMode.FABRIC8_EFFECTIVE_PLATFORM_MODE, tc.mode.name());

            // Setup mock behaviour
            new Expectations() {
                {
                    Configuration configuration =
                        new Configuration.Builder().properties(projectProps).processorConfig(config).build();
                    context.getConfiguration();
                    result = configuration;
                    project.getArtifactId();
                    result = "projectA";
                    minTimes = 0;
                    context.getGav();
                    result = new GroupArtifactVersion("", "projectA", "0");
                }
            };

            AutoTLSEnricher enricher = new AutoTLSEnricher(context);
            Map<String, String> annotations = enricher.getAnnotations(Kind.SERVICE);
            if (tc.mode == PlatformMode.kubernetes) {
                assertNull(annotations);
                continue;
            }

            assertEquals(1, annotations.size());
            assertEquals(tc.tlsSecretName, annotations.get(AutoTLSEnricher.AUTOTLS_ANNOTATION_KEY));
        }
    }

    private static final class AdaptTestConfig {
        private final PlatformMode mode;
        private final String initContainerNameConfig;
        private final String initContainerName;
        private final String initContainerImageConfig;
        private final String initContainerImage;
        private final String tlsSecretVolumeNameConfig;
        private final String tlsSecretVolumeName;
        private final String jksVolumeNameConfig;
        private final String jksVolumeName;

        private AdaptTestConfig(PlatformMode mode, String initContainerNameConfig, String initContainerName,
                String initContainerImageConfig, String initContainerImage, String tlsSecretVolumeNameConfig,
                String tlsSecretVolumeName, String jksVolumeNameConfig, String jksVolumeName) {
            this.mode = mode;
            this.initContainerNameConfig = initContainerNameConfig;
            this.initContainerName = initContainerName;
            this.initContainerImageConfig = initContainerImageConfig;
            this.initContainerImage = initContainerImage;
            this.tlsSecretVolumeNameConfig = tlsSecretVolumeNameConfig;
            this.tlsSecretVolumeName = tlsSecretVolumeName;
            this.jksVolumeNameConfig = jksVolumeNameConfig;
            this.jksVolumeName = jksVolumeName;
        }
    }

    @Test
    public void testAdapt() throws Exception {
        final AdaptTestConfig[] data = new AdaptTestConfig[] {
                new AdaptTestConfig(PlatformMode.kubernetes, null, null, null, null, null, null, null, null),
                new AdaptTestConfig(PlatformMode.openshift, null, "tls-jks-converter", null,
                        "jimmidyson/pemtokeystore:v0.1.0", null, "tls-pem", null, "tls-jks"),
                new AdaptTestConfig(PlatformMode.openshift, null, "tls-jks-converter", null,
                        "jimmidyson/pemtokeystore:v0.1.0", "tls-a", "tls-a", null, "tls-jks"),
                new AdaptTestConfig(PlatformMode.openshift, null, "tls-jks-converter", null,
                        "jimmidyson/pemtokeystore:v0.1.0", null, "tls-pem", "jks-b", "jks-b"),
                new AdaptTestConfig(PlatformMode.openshift, "test-container-name", "test-container-name", "image/123",
                        "image/123", "tls-a", "tls-a", "jks-b", "jks-b") };

        for (final AdaptTestConfig tc : data) {
            TreeMap configMap = new TreeMap() {
                {
                    put(AutoTLSEnricher.Config.pemToJKSInitContainerName.name(), tc.initContainerNameConfig);
                    put(AutoTLSEnricher.Config.pemToJKSInitContainerImage.name(), tc.initContainerImageConfig);
                    put(AutoTLSEnricher.Config.tlsSecretVolumeName.name(), tc.tlsSecretVolumeNameConfig);
                    put(AutoTLSEnricher.Config.jksVolumeName.name(), tc.jksVolumeNameConfig);
                }
            };
            final ProcessorConfig config = new ProcessorConfig(null, null,
                    Collections.singletonMap(AutoTLSEnricher.ENRICHER_NAME, configMap));

            final Properties projectProps = new Properties();
            projectProps.put(PlatformMode.FABRIC8_EFFECTIVE_PLATFORM_MODE, tc.mode.name());

            // Setup mock behaviour
            new Expectations() {
                {
                    Configuration configuration =
                        new Configuration.Builder()
                            .properties(projectProps)
                            .processorConfig(config)
                            .build();
                    context.getConfiguration();
                    result = configuration;
                    project.getArtifactId();
                    result = "projectA";
                    minTimes = 0;
                }
            };

            AutoTLSEnricher enricher = new AutoTLSEnricher(context);
            KubernetesListBuilder klb = new KubernetesListBuilder().addNewPodTemplateItem().withNewMetadata().and()
                    .withNewTemplate().withNewMetadata().and().withNewSpec().and().and().and();
            enricher.adapt(PlatformMode.kubernetes, klb);
            PodTemplate pt = (PodTemplate) klb.getItems().get(0);

            List<Container> initContainers = pt.getTemplate().getSpec().getInitContainers();
            assertEquals(tc.mode == PlatformMode.openshift, !initContainers.isEmpty());

            if (tc.mode == PlatformMode.kubernetes) {
                continue;
            }

            Gson gson = new Gson();
            JsonArray ja = new JsonParser().parse(gson.toJson(initContainers, new TypeToken<Collection<Container>>() {}.getType())).getAsJsonArray();
            assertEquals(1, ja.size());
            JsonObject jo = ja.get(0).getAsJsonObject();
            assertEquals(tc.initContainerName, jo.get("name").getAsString());
            assertEquals(tc.initContainerImage, jo.get("image").getAsString());
            JsonArray mounts = jo.get("volumeMounts").getAsJsonArray();
            assertEquals(2, mounts.size());
            JsonObject mount = mounts.get(0).getAsJsonObject();
            assertEquals(tc.tlsSecretVolumeName, mount.get("name").getAsString());
            mount = mounts.get(1).getAsJsonObject();
            assertEquals(tc.jksVolumeName, mount.get("name").getAsString());
        }
    }
}
