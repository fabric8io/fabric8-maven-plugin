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

/**
 */
public class MapUtil {

    private MapUtil() {}

    /**
     * Adds the given key and value pair into the map if the map does not already contain a value for that key
     */
    public static void putIfAbsent(Map<String, String> map, String name, String value) {
        if (!map.containsKey(name)) {
            map.put(name, value);
        }
    }

    /**
     * Add all values of a map to another map, but onlfy if not already existing.
     * @param map target map
     * @param toMerge the values to ad
     */
    public static void mergeIfAbsent(Map<String, String> map, Map<String, String> toMerge) {
        for (Map.Entry<String, String> entry : toMerge.entrySet()) {
            putIfAbsent(map, entry.getKey(), entry.getValue());;
        }
    }

    /**
     * Returns a new map with all the entries of map1 and any from map2 which don't override map1.
     *
     * Can handle either maps being null. Always returns a new mutable map
     */
    public static <K,V> Map<K,V> mergeMaps(Map<K, V> map1, Map<K, V> map2) {
        Map<K, V> answer = new HashMap<>();
        if (map2 != null) {
            answer.putAll(map2);
        }
        if (map1 != null) {
            answer.putAll(map1);
        }
        return answer;

    }

    /**
     * Copies all of the elements i.e., the mappings, from toPut map into ret, if toPut isn't null.
     * @param ret
     * @param toPut
     */
    public static void putAllIfNotNull(Map<String, String> ret, Map<String, String> toPut) {
        if (toPut != null) {
            ret.putAll(toPut);
        }
    }

}
