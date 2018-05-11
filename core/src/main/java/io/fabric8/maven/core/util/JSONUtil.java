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

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;

/**
 * Utility for JSON Jandlong
 *
 * @author roland
 * @since 07/02/17
 */
public class JSONUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static ObjectMapper mapper() {
        return OBJECT_MAPPER;
    }

    public static boolean equals(JSONObject first, JSONObject second) {

        try {
            final JsonNode tree1 = OBJECT_MAPPER.readTree(first.toString());
            final JsonNode tree2 = OBJECT_MAPPER.readTree(second.toString());
            return tree1.equals(tree2);
        } catch (IOException e) {
            return false;
        }
    }
}
