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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.json.JSONObject;

/**
 * Utility for resource file handling
 *
 * @author roland
 * @since 07/02/17
 */
public class ResourceUtil {

    public static boolean jsonEquals(JSONObject first, JSONObject second) {
        final ObjectMapper mapper = new ObjectMapper();

        try {
            final JsonNode tree1 = mapper.readTree(first.toString());
            final JsonNode tree2 = mapper.readTree(second.toString());
            return tree1.equals(tree2);
        } catch (IOException e) {
            return false;
        }
    }

    public static <T> T load(File file, Class<T> clazz) throws IOException {
        ResourceFileType type = ResourceFileType.fromFile(file);
        return load(file, clazz, type);
    }

    public static <T> T load(File file, Class<T> clazz, ResourceFileType resourceFileType) throws IOException {
        return getObjectMapper(resourceFileType).readValue(file, clazz);
    }

    public static <T> T load(InputStream in, Class<T> clazz, ResourceFileType resourceFileType) throws IOException {
        return getObjectMapper(resourceFileType).readValue(in, clazz);
    }

    public static File save(File file, Object data) throws IOException {
        return save(file, data, ResourceFileType.fromFile(file));
    }

    public static File save(File file, Object data, ResourceFileType type) throws IOException {
        File output = type.addExtensionIfMissing(file);
        ensureDir(file);
        getObjectMapper(type).writeValue(output, data);
        return output;
    }


    public static String toYaml(Object resource) throws JsonProcessingException {
        return serializeAsString(resource, ResourceFileType.yaml);
    }

    public static String toJson(Object resource) throws JsonProcessingException {
        return serializeAsString(resource, ResourceFileType.json);
    }

    private static String serializeAsString(Object resource, ResourceFileType resourceFileType) throws JsonProcessingException {
        return getObjectMapper(resourceFileType).writeValueAsString(resource);
    }

    private static ObjectMapper getObjectMapper(ResourceFileType resourceFileType) {
        return resourceFileType.getObjectMapper()
                               .enable(SerializationFeature.INDENT_OUTPUT)
                               .disable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS)
                               .disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
    }

    private static void ensureDir(File file) throws IOException {
        File parentDir = file.getParentFile();
        if (!parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Cannot create directory " + parentDir);
            }
        }
    }

}
