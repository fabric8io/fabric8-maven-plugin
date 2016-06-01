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

package io.fabric8.maven.core.util;

import java.io.*;
import java.net.URLDecoder;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import org.junit.BeforeClass;
import org.junit.Test;

import static io.fabric8.maven.core.util.KubernetesResourceUtil.API_VERSION;
import static io.fabric8.maven.core.util.KubernetesResourceUtil.API_EXTENSIONS_VERSION;
import static io.fabric8.maven.core.util.KubernetesResourceUtil.getKubernetesResource;
import static io.fabric8.maven.docker.config.AssemblyMode.dir;
import static org.junit.Assert.*;

/**
 * @author roland
 * @since 02/05/16
 */
public class KubernetesResourceUtilTest {

    private static File fabric8Dir;

    @BeforeClass
    public static void initPath() throws UnsupportedEncodingException {
        ClassLoader classLoader = KubernetesResourceUtil.class.getClassLoader();
        String filePath = URLDecoder.decode(classLoader.getResource("fabric8/simple-rc.yaml").getFile(), "UTF-8");
        fabric8Dir = new File(filePath).getParentFile();
    }

    @Test
    public void simple() throws IOException {
        for (String ext : new String[] { "yaml", "json" }) {
            HasMetadata ret = getKubernetesResource(API_VERSION, API_EXTENSIONS_VERSION, new File(fabric8Dir, "simple-rc." + ext));
            assertEquals(API_VERSION, ret.getApiVersion());
            assertEquals("ReplicationController", ret.getKind());
            assertEquals("simple", ret.getMetadata().getName());
        }
    }

    @Test
    public void withValue() throws IOException {
        HasMetadata ret = getKubernetesResource(API_VERSION, API_EXTENSIONS_VERSION, new File(fabric8Dir, "named-svc.yaml"));
        assertEquals(API_VERSION, ret.getApiVersion());
        assertEquals("Service", ret.getKind());
        assertEquals("pong", ret.getMetadata().getName());
    }

    @Test
    public void invalidType() throws IOException {
        try {
            getKubernetesResource(API_VERSION, API_EXTENSIONS_VERSION, new File(fabric8Dir, "simple-bla.yaml"));
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains("bla"));
            assertTrue(exp.getMessage().contains("svc"));
        }
    }

    @Test
    public void containsKind() throws Exception {
        HasMetadata ret = getKubernetesResource(API_VERSION, API_EXTENSIONS_VERSION, new File(fabric8Dir, "contains_kind.yml"));
        assertEquals("ReplicationController", ret.getKind());
    }

    @Test
    public void containsNoKindAndNoTypeInFilename() throws Exception {
        try {
            getKubernetesResource(API_VERSION, API_EXTENSIONS_VERSION, new File(fabric8Dir, "contains_no_kind.yml"));
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains("type"));
            assertTrue(exp.getMessage().toLowerCase().contains("kind"));
        }


    }

    @Test
    public void invalidPattern() throws IOException {
        try {
            getKubernetesResource(API_VERSION, API_EXTENSIONS_VERSION, new File(fabric8Dir, "blubber.yaml"));
            fail();
        } catch (FileNotFoundException exp) {
            assertTrue(exp.getMessage().contains("blubber"));
        }
    }

    @Test
    public void noNameInFile() throws IOException {
        HasMetadata ret = getKubernetesResource(API_VERSION, API_EXTENSIONS_VERSION, new File(fabric8Dir, "rc.yml"));
        assertEquals("flipper",ret.getMetadata().getName());
    }

    @Test
    public void noNameInFileAndNotInMetadata() throws IOException {
        HasMetadata ret = getKubernetesResource(API_VERSION, API_EXTENSIONS_VERSION, new File(fabric8Dir, "svc.yml"));
        assertEquals("Service",ret.getKind());
        assertNull(ret.getMetadata().getName());
    }

    @Test
    public void invalidExtension() throws IOException {
        try {
            getKubernetesResource(API_VERSION, API_EXTENSIONS_VERSION, new File(fabric8Dir, "simple-rc.txt"));
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains("txt"));
            assertTrue(exp.getMessage().contains("json"));
            assertTrue(exp.getMessage().contains("yml"));
        }
    }

    @Test
    public void readWholeDir() throws IOException {
        KubernetesListBuilder builder =
            KubernetesResourceUtil.readResourceFragmentsFrom("v2", "extensions/v2", new File(fabric8Dir, "read-dir").listFiles());
        KubernetesList list = builder.build();
        assertEquals(2,list.getItems().size());
        for (HasMetadata item : list.getItems() ) {
            assertTrue("Service".equals(item.getKind()) || "ReplicationController".equals(item.getKind()));
            assertEquals("pong",item.getMetadata().getName());
            assertEquals("v2",item.getApiVersion());
        }
    }
}

