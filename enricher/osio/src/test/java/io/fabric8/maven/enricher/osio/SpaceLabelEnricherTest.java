/*
 * Copyright 2018 Red Hat, Inc.
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

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import com.google.common.collect.ImmutableMap;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(JMockit.class)
public class SpaceLabelEnricherTest {

    @Mocked
    private EnricherContext context;

    private static final String SPACE_KEY = "fabric8.enricher.osio-space-label.space";
    private static final String SPACE_VALUE = "test";

    @Test
    public void testDefaultConfiguration() {
        final Properties props = new Properties();
        new Expectations() {{
            context.getProject().getProperties();
            result = props;
        }};

        SpaceLabelEnricher enricher = new SpaceLabelEnricher(context);
        Map<String, String> labels = enricher.getLabels(Kind.SERVICE);
        assertNull(labels);
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
            context.getProject().getProperties();
            result = props;
        }};

        SpaceLabelEnricher enricher = new SpaceLabelEnricher(context);
        Map<String, String> labels = enricher.getLabels(Kind.SERVICE);
        checkLabel(labels);
    }

    @Test
    public void testLabelMavenProperty() {
        final Properties props = new Properties();
        props.put(SPACE_KEY, SPACE_VALUE);
        new Expectations() {{
            context.getProject().getProperties();
            result = props;
        }};

        SpaceLabelEnricher enricher = new SpaceLabelEnricher(context);
        Map<String, String> labels = enricher.getLabels(Kind.SERVICE);
        checkLabel(labels);
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
            context.getConfig();
            result = config;
        }};

        SpaceLabelEnricher enricher = new SpaceLabelEnricher(context);
        Map<String, String> labels = enricher.getLabels(Kind.SERVICE);
        checkLabel(labels);
    }

    private void checkLabel(Map<String, String> labels) {
        assertNotNull(labels);
        assertEquals(1, labels.size());
        assertEquals(SPACE_VALUE, labels.get("space"));
    }

}
