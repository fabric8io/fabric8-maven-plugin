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

package io.fabric8.maven.core.service;/*
 *
 * Copyright 2015-2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.*;
import java.util.*;

import io.fabric8.kubernetes.client.dsl.*;
import io.fabric8.kubernetes.client.dsl.base.BaseOperation;
import io.fabric8.maven.core.util.KubernetesResourceUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.openshift.api.model.*;
import io.fabric8.openshift.client.OpenShiftClient;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.yaml.snakeyaml.Yaml;

import static org.junit.Assert.*;

/**
 * @author roland
 * @since 16/01/17
 */
@RunWith(JMockit.class)
public class ImageStreamServiceTest {

    @Mocked
    OpenShiftClient client;

    @Mocked
    BaseOperation imageStreamsOp;

    @Mocked
    ClientResource resource;

    @Mocked
    Logger log;

    @Test
    public void simple() throws IOException, MojoExecutionException {
        ImageStreamService service = new ImageStreamService(client, log);

        final ImageStream lookedUpIs = lookupImageStream("ab12cd");
        new Expectations() {{
            client.imageStreams(); result = imageStreamsOp;
            imageStreamsOp.withName("test"); result = resource;
            resource.get(); result = lookedUpIs;

            client.getNamespace(); result = "default";
        }};
        ImageName name = new ImageName("test:1.0");
        File target = File.createTempFile("ImageStreamServiceTest",".yml");
        service.saveImageStreamResource(name, target);

        assertTrue(target.exists());

        Yaml yaml = new Yaml();
        InputStream ios = new FileInputStream(target);
        // Parse the YAML file and return the output as a series of Maps and Lists
        Map result = (Map<String,Object>) yaml.load(ios);
        System.out.println(result.toString());
        assertNotNull(result);
        List items = (List) result.get("items");
        assertNotNull(items);
        assertEquals(1, items.size());
        Map isRead = (Map<String, Object>) items.get(0);
        assertNotNull(isRead);
        assertEquals("ImageStream", isRead.get("kind"));
        Map spec = (Map<String, Object>) isRead.get("spec");
        assertNotNull(spec);
        List tags = (List) spec.get("tags");
        assertNotNull(tags);
        assertEquals(1,tags.size());
        Map tag = (Map) tags.get(0);
        Map from = (Map) tag.get("from");
        assertEquals("ImageStreamImage", from.get("kind"));
        assertEquals("test@ab12cd", from.get("name"));
        assertEquals("default", from.get("namespace"));
    }

    private ImageStream lookupImageStream(String sha) {
        NamedTagEventList list = new NamedTagEventList();
        TagEvent tag = new TagEvent();
        tag.setImage(sha);
        list.setItems(new ArrayList<TagEvent>(Arrays.asList(tag)));

        return new ImageStreamBuilder()
            .withNewStatus()
            .addToTags(list)
            .endStatus()
            .build();
    }
}
