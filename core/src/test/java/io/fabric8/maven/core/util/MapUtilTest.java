/**
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

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author roland
 * @since 05/08/16
 */
public class MapUtilTest {

    @Test
    public void testMergeIfAbsent() {
        Map<String, String> origMap = createMap("eins", "one", "zwei", "two");
        Map<String, String> toMergeMap = createMap("zwei", "deux", "drei", "trois");
        Map<String, String> expected = createMap("eins", "one", "zwei", "two", "drei", "trois");
        MapUtil.mergeIfAbsent(origMap, toMergeMap);
        assertEquals(expected, origMap);
    }

    @Test
    public void testPutIfAbsent() {
        Map<String, String> map = createMap("eins", "one");
        MapUtil.putIfAbsent(map, "eins", "un");
        assertEquals(1,map.size());
        assertEquals("one", map.get("eins"));
        MapUtil.putIfAbsent(map, "zwei", "deux");
        assertEquals(2, map.size());
        assertEquals("one", map.get("eins"));
        assertEquals("deux", map.get("zwei"));
    }

    @Test
    public void testMergeMaps() {
        Map<String, String> mapA = createMap("eins", "one", "zwei", "two");
        Map<String, String> mapB = createMap("zwei", "deux", "drei", "trois");
        Map<String, String> expectedA = createMap("eins", "one", "zwei", "two", "drei", "trois");
        Map<String, String> expectedB = createMap("eins", "one", "zwei", "deux", "drei", "trois");

        assertEquals(expectedA, MapUtil.mergeMaps(mapA, mapB));
        assertEquals(expectedB, MapUtil.mergeMaps(mapB, mapA));
    }


    private Map<String,String> createMap(String ... args) {
        Map<String, String> ret = new HashMap<>();
        for (int i = 0; i < args.length; i+=2) {
            ret.put(args[i], args[i+1]);
        }
        return ret;
    }
}
