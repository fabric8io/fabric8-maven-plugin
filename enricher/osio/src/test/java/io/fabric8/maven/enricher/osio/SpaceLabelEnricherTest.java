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
package io.fabric8.maven.enricher.osio;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.model.Configuration;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SpaceLabelEnricherTest {

    @Mocked
    private MavenEnricherContext context;

    private static final String SPACE_KEY = "fabric8.enricher.osio-space-label.space";
    private static final String SPACE_VALUE = "test";

    @Test
    public void testDefaultConfiguration() {
        final Properties props = new Properties();
        new Expectations() {{
            context.getConfiguration(); result = new Configuration.Builder().properties(props).build();
        }};

        Service service = new ServiceBuilder().withNewMetadata().withName("foo").endMetadata().build();
        SpaceLabelEnricher enricher = new SpaceLabelEnricher(context);
        enricher.create(PlatformMode.kubernetes, new KubernetesListBuilder().withItems(service));
        assertNotNull(service.getMetadata().getLabels());
    }

    @Test
    public void testLabelSystemProperty() {
        new MockUp<System>() {
            @Mock String getProperty(String name) {
                if (SPACE_KEY.equals(name)) {
                    return SPACE_VALUE;
                }
                return null;
            }
        };

        final Properties props = new Properties();
        new Expectations() {{
            context.getConfiguration(); result = new Configuration.Builder().properties(props).build();
        }};

        Service service = new ServiceBuilder().withNewMetadata().withName("foo").endMetadata().build();
        SpaceLabelEnricher enricher = new SpaceLabelEnricher(context);
        KubernetesListBuilder builder = new KubernetesListBuilder().withItems(service);
        enricher.create(PlatformMode.kubernetes, builder);
        service = (Service) builder.buildFirstItem();
        checkLabel(service.getMetadata().getLabels());
    }

    @Test
    public void testLabelMavenProperty() {
        final Properties props = new Properties();
        props.put(SPACE_KEY, SPACE_VALUE);
        new Expectations() {{
            context.getConfiguration(); result = new Configuration.Builder().properties(props).build();
        }};

        Service service = new ServiceBuilder().withNewMetadata().withName("foo").endMetadata().build();
        SpaceLabelEnricher enricher = new SpaceLabelEnricher(context);
        KubernetesListBuilder builder = new KubernetesListBuilder().withItems(service);
        enricher.create(PlatformMode.kubernetes, builder);
        service = (Service) builder.buildFirstItem();
        checkLabel(service.getMetadata().getLabels());
    }

    @Test
    public void testLabelPluginConfig() {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        final ProcessorConfig config = new ProcessorConfig(
                null,
                null,
                Collections.singletonMap(
                        "osio-space-label",
                        new TreeMap(
                                ImmutableMap.of(
                                        SpaceLabelEnricher.Config.space.name(),
                                        SPACE_VALUE
                                ))
                )
        );

        new Expectations() {{
            context.getConfiguration(); result = new Configuration.Builder().processorConfig(config).build();
        }};

        Service service = new ServiceBuilder().withNewMetadata().withName("foo").endMetadata().build();
        SpaceLabelEnricher enricher = new SpaceLabelEnricher(context);
        KubernetesListBuilder builder = new KubernetesListBuilder().withItems(service);
        enricher.create(PlatformMode.kubernetes, builder);
        service = (Service) builder.buildFirstItem();
        checkLabel(service.getMetadata().getLabels());
    }

    private void checkLabel(Map<String, String> labels) {
        assertNotNull(labels);
        assertEquals(1, labels.size());
        assertEquals(SPACE_VALUE, labels.get("space"));
    }

}
