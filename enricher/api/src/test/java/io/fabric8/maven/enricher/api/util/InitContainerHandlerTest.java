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

package io.fabric8.maven.enricher.api.util;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.maven.core.util.JSONUtil;
import io.fabric8.maven.docker.util.Logger;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * @author roland
 * @since 07/02/17
 */

@RunWith(JMockit.class)
public class InitContainerHandlerTest {

    @Mocked
    Logger log;

    InitContainerHandler handler;

    @Before
    public void setUp() {
        handler = new InitContainerHandler(log);
    }

    @Test
    public void simple() {
        PodTemplateSpecBuilder builder = getPodTemplateBuilder();
        assertFalse(handler.hasInitContainer(builder, "blub"));
        JSONObject initContainer = createInitContainer("blub", "foo/blub");
        handler.appendInitContainer(builder, initContainer);
        assertTrue(handler.hasInitContainer(builder, "blub"));
        verifyBuilder(builder,initContainer);
    }

    @Test
    public void append() {
        PodTemplateSpecBuilder builder = getPodTemplateBuilder("bla", "foo/bla");
        assertFalse(handler.hasInitContainer(builder, "blub"));
        JSONObject initContainer = createInitContainer("blub", "foo/blub");
        handler.appendInitContainer(builder, initContainer);
        assertTrue(handler.hasInitContainer(builder, "blub"));
        verifyBuilder(builder,createInitContainer("bla", "foo/bla"), initContainer);
    }

    @Test
    public void removeAll() {
        PodTemplateSpecBuilder builder = getPodTemplateBuilder("bla", "foo/bla");
        assertTrue(handler.hasInitContainer(builder, "bla"));
        handler.removeInitContainer(builder, "bla");
        assertFalse(handler.hasInitContainer(builder, "bla"));
        verifyBuilder(builder);
    }

    @Test
    public void removeOne() {
        PodTemplateSpecBuilder builder = getPodTemplateBuilder("bla", "foo/bla", "blub", "foo/blub");
        assertTrue(handler.hasInitContainer(builder, "bla"));
        assertTrue(handler.hasInitContainer(builder, "blub"));
        handler.removeInitContainer(builder, "bla");
        assertFalse(handler.hasInitContainer(builder, "bla"));
        assertTrue(handler.hasInitContainer(builder, "blub"));
        verifyBuilder(builder, createInitContainer("blub", "foo/blub"));
    }

    @Test
    public void existingSame() {
        new Expectations() {{
            log.warn(anyString, withSubstring("blub"));
        }};

        PodTemplateSpecBuilder builder = getPodTemplateBuilder("blub", "foo/blub");
        assertTrue(handler.hasInitContainer(builder, "blub"));
        JSONObject initContainer = createInitContainer("blub", "foo/blub");
        handler.appendInitContainer(builder, initContainer);
        assertTrue(handler.hasInitContainer(builder, "blub"));
        verifyBuilder(builder, initContainer);
    }

    @Test
    public void existingDifferent() {
        try {
            PodTemplateSpecBuilder builder = getPodTemplateBuilder("blub", "foo/bla");
            assertTrue(handler.hasInitContainer(builder, "blub"));
            JSONObject initContainer = createInitContainer("blub", "foo/blub");
            handler.appendInitContainer(builder, initContainer);
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains("blub"));
        }
    }

    private void verifyBuilder(PodTemplateSpecBuilder builder, JSONObject ... initContainers) {
        PodTemplateSpec spec = builder.build();
        String containers = spec.getMetadata().getAnnotations().get(InitContainerHandler.INIT_CONTAINER_ANNOTATION);
        if (initContainers.length == 0) {
            assertNull(containers);
        } else {
            JSONArray got = new JSONArray(containers);
            assertEquals(got.length(), initContainers.length);
            for (int i = 0; i < initContainers.length; i++) {
                assertTrue(JSONUtil.equals(got.getJSONObject(i), initContainers[i]));
            }
        }
    }

    private PodTemplateSpecBuilder getPodTemplateBuilder(String ... definitions) {
        PodTemplateSpecBuilder ret = new PodTemplateSpecBuilder();
        ret.withNewMetadata()
           .withAnnotations(getInitContainerAnnotation(definitions))
           .endMetadata();
        return ret;
    }

    private Map<String, String> getInitContainerAnnotation(String ... definitions) {
        Map<String, String> ret = new HashMap<>();
        JSONArray initContainers = new JSONArray();
        for (int i = 0; i < definitions.length; i += 2 ) {
            initContainers.put(createInitContainer(definitions[i], definitions[i+1]));
        }
        if (initContainers.length() > 0) {
            ret.put(InitContainerHandler.INIT_CONTAINER_ANNOTATION, initContainers.toString());
        }
        return ret;
    }

    private JSONObject createInitContainer(String name, String image) {
        JSONObject initContainer = new JSONObject();
        initContainer.put("name", name);
        initContainer.put("image", image);
        return initContainer;
    }
}
