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
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.model.Configuration;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import io.fabric8.maven.enricher.api.Kind;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PrometheusEnricherTest {

    @Mocked
    private MavenEnricherContext context;
    @Mocked
    ImageConfiguration imageConfiguration;

    private enum Config implements Configs.Key {
        prometheusPort;
        public String def() { return d; } protected String d;
    }

    // *******************************
    // Tests
    // *******************************

    @Test
    public void testCustomPrometheusPort() throws Exception {
        final ProcessorConfig config = new ProcessorConfig(
            null,
            null,
            Collections.singletonMap(
                PrometheusEnricher.ENRICHER_NAME,
                new TreeMap(Collections.singletonMap(
                    Config.prometheusPort.name(),
                    "1234")
                )
            )
        );

        // Setup mock behaviour
        new Expectations() {{
            context.getConfiguration(); result = new Configuration.Builder().processorConfig(config).build();
        }};

        PrometheusEnricher enricher = new PrometheusEnricher(context);
        Map<String, String> annotations = enricher.getAnnotations(Kind.SERVICE);

        assertEquals(2, annotations.size());
        assertEquals("1234", annotations.get(PrometheusEnricher.ANNOTATION_PROMETHEUS_PORT));
        assertEquals("true", annotations.get(PrometheusEnricher.ANNOTATION_PROMETHEUS_SCRAPE));
    }

    @Test
    public void testDetectPrometheusPort() throws Exception {
        final ProcessorConfig config = new ProcessorConfig(
            null,
            null,
            Collections.singletonMap(
                PrometheusEnricher.ENRICHER_NAME,
                new TreeMap()
            )
        );

        final BuildImageConfiguration imageConfig = new BuildImageConfiguration.Builder()
            .ports(Arrays.asList(PrometheusEnricher.PROMETHEUS_PORT))
            .build();


        // Setup mock behaviour
        new Expectations() {{
            context.getConfiguration();
            result = new Configuration.Builder()
                .processorConfig(config)
                .images(Arrays.asList(imageConfiguration))
                .build();

            imageConfiguration.getBuildConfiguration(); result = imageConfig;
        }};

        PrometheusEnricher enricher = new PrometheusEnricher(context);
        Map<String, String> annotations = enricher.getAnnotations(Kind.SERVICE);

        assertEquals(2, annotations.size());
        assertEquals("9779", annotations.get(PrometheusEnricher.ANNOTATION_PROMETHEUS_PORT));
        assertEquals("true", annotations.get(PrometheusEnricher.ANNOTATION_PROMETHEUS_SCRAPE));
    }

    @Test
    public void testNoDefinedPrometheusPort() throws Exception {
        final ProcessorConfig config = new ProcessorConfig(
            null,
            null,
            Collections.singletonMap(
                PrometheusEnricher.ENRICHER_NAME,
                new TreeMap()
            )
        );

        final BuildImageConfiguration imageConfig = new BuildImageConfiguration.Builder()
            .build();

        // Setup mock behaviour
        new Expectations() {{
            context.getConfiguration();
            result = new Configuration.Builder()
                .processorConfig(config)
                .images(Arrays.asList(imageConfiguration))
                .build();

            imageConfiguration.getBuildConfiguration(); result = imageConfig;
        }};

        PrometheusEnricher enricher = new PrometheusEnricher(context);
        Map<String, String> annotations = enricher.getAnnotations(Kind.SERVICE);

        assertNull(annotations);
    }
}
