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

package io.fabric8.maven.core.config;

import java.util.*;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author roland
 * @since 24/07/16
 */
public class ProcessorConfigTest {

    List<String> includes = Arrays.asList("i1", "i2", "i3");
    Set<String> excludes = new HashSet<>(Arrays.asList("e1"));
    Map <String, TreeMap> config = Collections.singletonMap("k1", new TreeMap(Collections.singletonMap("i1","v1")));

    @Test
    public void incAndExc() {
        ProcessorConfig pConfig = new ProcessorConfig(includes, excludes, config);

        assertTrue(pConfig.use("i2"));
        assertFalse(pConfig.use("e1"));
        assertFalse(pConfig.use("n1"));
    }

    @Test
    public void inc() {
        ProcessorConfig pConfig = new ProcessorConfig(includes, null, config);

        assertTrue(pConfig.use("i2"));
        assertFalse(pConfig.use("e1"));
        assertFalse(pConfig.use("n1"));
    }

    @Test
    public void exc() {
        ProcessorConfig pConfig = new ProcessorConfig(null, excludes, config);

        assertTrue(pConfig.use("i2"));
        assertFalse(pConfig.use("e1"));
        assertTrue(pConfig.use("n1"));
    }


    @Test
    public void none() {
        ProcessorConfig pConfig = new ProcessorConfig(null, null, config);

        assertTrue(pConfig.use("i2"));
        assertTrue(pConfig.use("e1"));
        assertTrue(pConfig.use("n1"));
    }

    @Test
    public void empty() {
        ProcessorConfig pConfig = new ProcessorConfig(Collections.<String>emptyList(), null, config);

        assertFalse(pConfig.use("i2"));
        assertFalse(pConfig.use("e1"));
        assertFalse(pConfig.use("n1"));

    }
    @Test
    public void config() {
        ProcessorConfig pConfig = new ProcessorConfig(null, null, config);

        assertEquals("v1", pConfig.getConfig("k1", "i1"));
        assertNull(pConfig.getConfig("k2", "i1"));
        assertNull(pConfig.getConfig("k1", "i2"));
    }

    @Test
    public void order() {
        List<TestNamed> data = Arrays.asList(
            new TestNamed("t1"),
            new TestNamed("t2"),
            new TestNamed("t3"),
            new TestNamed("t4"));

        List<String> inc = Arrays.asList("t4", "t2");

        ProcessorConfig pConfig = new ProcessorConfig(inc, null, null);
        List<TestNamed> result = pConfig.order(data,"test");
        assertEquals(2,result.size());
        assertEquals("t4", result.get(0).getName());
        assertEquals("t2", result.get(1).getName());
    }

    @Test
    public void orderWithInvalidInc() {
        List<TestNamed> data = Arrays.asList(new TestNamed("t1"));
        List<String> inc = Arrays.asList("t3", "t1");

        ProcessorConfig pConfig = new ProcessorConfig(inc, null, null);
        try {
            pConfig.order(data, "bla");
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains("bla"));
        }
    }

    @Test
    public void orderWithEmptyInclude() {
        List<TestNamed> data = Arrays.asList(new TestNamed("t1"), new TestNamed("t2"));

        ProcessorConfig pConfig = ProcessorConfig.EMPTY;
        List<TestNamed> result = pConfig.order(data, "bla");
        assertEquals(data,result);
    }

    private class TestNamed implements Named {

        private String name;

        @Override
        public String getName() {
            return name;
        }

        public TestNamed(String name) {
            this.name = name;
        }
    }
}