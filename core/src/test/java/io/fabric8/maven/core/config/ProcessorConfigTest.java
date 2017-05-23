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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
        List<TestNamed> filtered = pConfig.prepareProcessors(getAllTestData(), "test");
        assertTrue(contains(filtered, "i2"));
        assertFalse(contains(filtered, "e1"));
        assertFalse(contains(filtered, "n1"));
    }

    @Test
    public void inc() {
        ProcessorConfig pConfig = new ProcessorConfig(includes, null, config);
        List<TestNamed> filtered = pConfig.prepareProcessors(getAllTestData(), "test");

        assertTrue(contains(filtered, "i2"));
        assertFalse(contains(filtered, "e1"));
        assertFalse(contains(filtered, "n1"));
    }

    @Test
    public void exc() {
        ProcessorConfig pConfig = new ProcessorConfig(null, excludes, config);
        List<TestNamed> filtered = pConfig.prepareProcessors(getAllTestData(), "test");

        assertFalse(contains(filtered, "i2"));
        assertFalse(contains(filtered, "e1"));
        assertFalse(contains(filtered, "n1"));
    }


    @Test
    public void empty() {
        ProcessorConfig pConfig = new ProcessorConfig(Collections.<String>emptyList(), null, config);
        List<TestNamed> filtered = pConfig.prepareProcessors(getAllTestData(), "test");

        assertFalse(contains(filtered, "i2"));
        assertFalse(contains(filtered, "e1"));
        assertFalse(contains(filtered, "n1"));
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
        List<TestNamed> result = pConfig.prepareProcessors(data, "test");
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
            pConfig.prepareProcessors(data, "bla");
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains("bla"));
        }
    }


    @Test
    public void merge() {
        Yaml yaml = new Yaml();
        List<Map> data = (List<Map>) yaml.load(getClass().getResourceAsStream("/fabric8/config/ProcessorConfigTest.yml"));

        for (Map entry : data) {
            List<Map> inputs = (List<Map>) entry.get("input");
            ArrayList<ProcessorConfig> processorConfigs  = new ArrayList<>();
            for (Map input : inputs) {
                processorConfigs.add(extractProcessorConfig(input));
            }
            ProcessorConfig merged = ProcessorConfig.mergeProcessorConfigs(processorConfigs.toArray(new ProcessorConfig[0]));
            ProcessorConfig expected = extractProcessorConfig((Map) entry.get("merged"));
            assertEquals(expected.includes, merged.includes);
            assertEquals(expected.excludes, merged.excludes);
            assertEquals(expected.config.keySet(), merged.config.keySet());
            for (Map.Entry<String, TreeMap> configEntry : merged.config.entrySet()) {
                TreeMap<String, String> expectedValues = expected.config.get(configEntry.getKey());
                TreeMap<String, String> mergedValues = configEntry.getValue();
                assertEquals(expectedValues.size(),mergedValues.size());
                for (Map.Entry<String, String> valEntry : mergedValues.entrySet()) {
                    assertEquals(expectedValues.get(valEntry.getKey()), valEntry.getValue());
                }
            }
        }
    }

    // =================================================================================

    private ProcessorConfig extractProcessorConfig(Map input) {
        List<String> i = (List<String>) input.get("includes");
        List<String> eL = (List<String>) input.get("excludes");
        Set<String> e = eL != null ? new HashSet(eL) : null;
        Map<String,Map> cV = (Map<String, Map>) input.get("config");
        Map<String, TreeMap> c = null;
        if (cV != null) {
            c = new HashMap<>();
            for (Map.Entry<String, Map> el : cV.entrySet()) {
                c.put(el.getKey(), new TreeMap(el.getValue()));
            }
        }
        return new ProcessorConfig(i, e, c);
    }



    private boolean contains(List<TestNamed> list, String element) {
        return list.contains(new TestNamed(element));
    }

    private List<TestNamed> getAllTestData() {
        return Arrays.asList(new TestNamed("i2"), new TestNamed("i1"), new TestNamed("i3"), new TestNamed("e1"));
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TestNamed testNamed = (TestNamed) o;

            if (name != null ? !name.equals(testNamed.name) : testNamed.name != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return name != null ? name.hashCode() : 0;
        }
    }
}