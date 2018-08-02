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

import java.io.IOException;
import java.util.List;

import io.fabric8.maven.core.util.ProfileUtil;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author roland
 * @since 03/01/17
 */
public class ProfileTest {

    @Test
    public void copy() throws IOException {
        Profile one = ProfileUtil.readAllFromClasspath("order-test-1", "").get(0);

        Profile copy = new Profile(one);
        assertEquals("order-test-1", copy.getName());
        assertTrue(copy.getGeneratorConfig().use("i1"));
        assertFalse(copy.getGeneratorConfig().use("e1"));
        assertFalse(copy.getEnricherConfig().use("i1"));
        assertNull(copy.getGeneratorConfig().getConfig("bla", "blub"));
    }

    @Test
    public void mergeDifferentNames() throws IOException {
        try {
            Profile one = ProfileUtil.readAllFromClasspath("order-test-1", "").get(0);
            Profile two = ProfileUtil.readAllFromClasspath("order-test-2", "").get(0);
            new Profile(one, two);
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains("order-test-1"));
            assertTrue(exp.getMessage().contains("order-test-2"));
        }
    }

    @Test
    public void merge1() throws Exception {
        merge("order-test-1",0, new String[] { "i3", "i1", "i4" });
    }

    @Test
    public void merge2() throws Exception {
        merge("order-test-2",1, new String[] { "i1", "i3", "i4" });
    }


    private void merge(String profile, int profileIndexInProfileTestYaml, String[] expected) throws Exception {
        Profile one = ProfileUtil.readAllFromClasspath(profile, "").get(0);; // in META-INF/fabric8/profiles.yml in test' resources
        Profile two = ProfileUtil.fromYaml(getClass().getResourceAsStream("/fabric8/config/ProfileTest.yml")).get(profileIndexInProfileTestYaml);

        for (Profile merge : new Profile[] {new Profile(one, two), new Profile(two, one)}) {
            ProcessorConfig config = merge.getGeneratorConfig();
            List<TN> procs = asList(new TN("i1"),new TN("i2"),new TN("i3"), new TN("i4"));
            List<TN> prepared = config.prepareProcessors(procs, "generator");
            for (int i = 0; i < prepared.size(); i++) {
                assertEquals(expected[i], prepared.get(i).getName());
            }
        }
    }

    @Test
    public void sort1() throws IOException {
        Profile one = ProfileUtil.readAllFromClasspath("order-test-3", "").get(0);; // in META-INF/fabric8/profiles.yml in test' resources
        Profile two = ProfileUtil.fromYaml(getClass().getResourceAsStream("/fabric8/config/ProfileTest.yml")).get(2);
        assertTrue(one.compareTo(two) < 0);
        assertTrue(two.compareTo(one) > 0);
        assertEquals(one.compareTo(one),0);
        assertEquals(two.compareTo(two),0);
    }

    @Test
    public void sort3() throws IOException {
        Profile one = ProfileUtil.readAllFromClasspath("order-test-1", "").get(0);; // in META-INF/fabric8/profiles.yml in test' resources
        Profile two = ProfileUtil.fromYaml(getClass().getResourceAsStream("/fabric8/config/ProfileTest.yml")).get(0);
        assertTrue(one.compareTo(two) < 0);
        assertTrue(two.compareTo(one) > 0);
    }

    @Test
    public void sort4_a() throws IOException {
        // Order of reading the profile is important
        Profile firstRead = ProfileUtil.readAllFromClasspath("order-test-3", "").get(0);; // in META-INF/fabric8/profiles.yml in test' resources
        Profile secondRead = ProfileUtil.fromYaml(getClass().getResourceAsStream("/fabric8/config/ProfileTest.yml")).get(2);
        assertTrue(firstRead.compareTo(secondRead) < 0);
        assertTrue(secondRead.compareTo(firstRead) > 0);
    }

    @Test
    public void sort4_b() throws IOException {
        // Order of reading the profile is important
        Profile firstRead = ProfileUtil.fromYaml(getClass().getResourceAsStream("/fabric8/config/ProfileTest.yml")).get(2);
        Profile secondRead = ProfileUtil.readAllFromClasspath("order-test-3", "").get(0);; // in META-INF/fabric8/profiles.yml in test' resources
        assertTrue(firstRead.compareTo(secondRead) < 0);
        assertTrue(secondRead.compareTo(firstRead) > 0);
    }

    private static class TN implements Named {

        private String name;

        private TN(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}