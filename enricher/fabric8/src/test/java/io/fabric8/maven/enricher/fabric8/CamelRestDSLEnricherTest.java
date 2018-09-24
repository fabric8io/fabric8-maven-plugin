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
package io.fabric8.maven.enricher.fabric8;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(JMockit.class)
public class CamelRestDSLEnricherTest {

    @Mocked
    private EnricherContext context;
    @Mocked
    ImageConfiguration imageConfiguration;

    private enum Config implements Configs.Key {
        springDir;
        public String def() { return d; } protected String d;
    }

    // *******************************
    // Tests
    // *******************************

    @Test
    public void testCustomSourceDir() throws Exception {
        String springDir = new File("src/test/resources/spring").getAbsolutePath();
        final ProcessorConfig config = new ProcessorConfig(
            null,
            null,
            Collections.singletonMap(
                CamelRestDSLEnricher.ENRICHER_NAME,
                new TreeMap(Collections.singletonMap(
                    Config.springDir.name(),
                    springDir)
                )
            )
        );

        // Setup mock behaviour
        new Expectations() {{
            context.getConfig(); result = config;
        }};

        CamelRestDSLEnricher enricher = new CamelRestDSLEnricher(context);
        Map<String, String> annotations = enricher.getAnnotations(Kind.SERVICE);

        assertEquals(5, annotations.size());
        assertEquals("http",         annotations.get(enricher.getDomain() + CamelRestDSLEnricher.SCHEME ));
        assertEquals("REST",         annotations.get(enricher.getDomain() + CamelRestDSLEnricher.PROTOCOL ));
        assertEquals("/",            annotations.get(enricher.getDomain() + CamelRestDSLEnricher.PATH ));
        assertEquals("OpenAPI",      annotations.get(enricher.getDomain() + CamelRestDSLEnricher.DESCRIPTION_LANGUAGE ));
        assertEquals("/openapi.json",annotations.get(enricher.getDomain() + CamelRestDSLEnricher.DESCRIPTION_PATH ));
    }


}
