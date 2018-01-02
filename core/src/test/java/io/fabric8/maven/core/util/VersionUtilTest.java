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

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 */
public class VersionUtilTest {
    @Test
    public void testEquals() throws Exception {
        assertVersionsEqual("1", "1");
        assertVersionsEqual("foo", "foo");
        assertVersionsEqual("foo-SNAPSHOT", "foo-SNAPSHOT");
        assertVersionsEqual("","");
        assertVersionsEqual(null,null);
    }

    @Test
    public void testGreater() throws Exception {
        assertVersionGreaterThan("1.5", "1.4.1");
        assertVersionGreaterThan("1.10", "1");
        assertVersionGreaterThan("1.10", "1.9");
        assertVersionGreaterThan("1.1", "1.0-SNAPSHOT");
        assertVersionGreaterThan("1.10", "1.9-SNAPSHOT");
        assertVersionGreaterThan("1.10-SNAPSHOT", "1.8");
        assertVersionGreaterThan("x-SNAPSHOT", "foo-SNAPSHOT");
        assertVersionGreaterThan("1.5","");
        assertVersionGreaterThan("1.5",null);
    }

    @Test
    public void testLess() throws Exception {
        assertVersionLessThan("1.4.1", "1.5");
    }

    public static void assertVersionsEqual(String v1, String v2) {
        int answer = VersionUtil.compareVersions(v1, v2);
        assertTrue("Version " + v1 + " compareTo " + v2 + " == 0 but got: " + answer, answer == 0);
    }

    public static void assertVersionGreaterThan(String v1, String v2) {
        int answer = VersionUtil.compareVersions(v1, v2);
        assertTrue("Version " + v1 + " compareTo " + v2 + " > 0 but got: " + answer, answer > 0);
    }

    public static void assertVersionLessThan(String v1, String v2) {
        int answer = VersionUtil.compareVersions(v1, v2);
        assertTrue("Version " + v1 + " compareTo " + v2 + " < 0 but got: " + answer, answer < 0);
    }

}
