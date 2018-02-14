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
package io.fabric8.maven.core.service.openshift;

import java.io.*;
import java.util.*;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.dsl.*;
import io.fabric8.kubernetes.client.dsl.base.BaseOperation;
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
    Resource resource;

    @Mocked
    Logger log;

    @Test
    public void simple() throws IOException, MojoExecutionException {
        ImageStreamService service = new ImageStreamService(client, log);

        final ImageStream lookedUpIs = lookupImageStream("ab12cdv1", "ab12cdv2");
        setupClientMock(lookedUpIs,"test");
        ImageName name = new ImageName("test:1.0");
        File target = File.createTempFile("ImageStreamServiceTest",".yml");
        service.appendImageStreamResource(name, target);

        assertTrue(target.exists());

        Map result = readImageStreamDescriptor(target);
        Yaml yaml;
        System.out.println(result.toString());
        assertNotNull(result);
        List<Map> items = getItemsList(result);
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
        assertEquals("test@ab12cdv2", from.get("name"));
        assertEquals("default", from.get("namespace"));

        // Add a second image stream
        ImageStream secondIs = lookupImageStream("secondIS");
        setupClientMock(secondIs, "second-test");
        ImageName name2 = new ImageName("second-test:1.0");
        service.appendImageStreamResource(name2, target);

        result = readImageStreamDescriptor(target);
        System.out.println(result.toString());
        items = getItemsList(result);
        assertEquals(2,items.size());
        Set<String> names = new HashSet<>(Arrays.asList("second-test", "test"));
        for (Map item : items) {
            assertTrue(names.remove( ((Map) item.get("metadata")).get("name")));
        }
        assertTrue(names.isEmpty());
    }

    private List<Map> getItemsList(Map result) {
        List items = (List) result.get("items");
        assertNotNull(items);
        return items;
    }

    private Map readImageStreamDescriptor(File target) throws FileNotFoundException {
        Yaml yaml = new Yaml();
        InputStream ios = new FileInputStream(target);
        // Parse the YAML file and return the output as a series of Maps and Lists
        return (Map<String,Object>) yaml.load(ios);
    }

    private void setupClientMock(final ImageStream lookedUpIs, final String name) {
        new Expectations() {{
            client.imageStreams(); result = imageStreamsOp;
            imageStreamsOp.withName(name); result = resource;
            resource.get(); result = lookedUpIs;

            client.getNamespace(); result = "default";
        }};
    }

    private ImageStream lookupImageStream(String ...shaList) {
        NamedTagEventList list = new NamedTagEventList();
        for (String sha: shaList) {
            TagEvent tag = new TagEvent();
            tag.setImage(sha);
            list.getItems().add(tag);
        }

        return new ImageStreamBuilder()
            .withNewStatus()
            .addToTags(list)
            .endStatus()
            .build();
    }
}
