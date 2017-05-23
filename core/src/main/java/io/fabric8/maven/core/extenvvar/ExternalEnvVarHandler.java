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

package io.fabric8.maven.core.extenvvar;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.fabric8.maven.core.util.MavenUtil;
import org.apache.maven.project.MavenProject;

/**
 */
public class ExternalEnvVarHandler {
    public static final String ENVIRONMENT_SCHEMA_FILE = "io/fabric8/environment/schema.json";
    private static ObjectMapper objectMapper = createObjectMapper();

    public static Map<String, String> getExportedEnvironmentVariables(MavenProject project, Map<String, String> envVars) {
        Map<String, String> ret = getEnvironmentVarsFromJsonSchema(project, envVars);
        ret.putAll(envVars);
        return ret;
    }

    // ==================================================================================================

    private static Map<String, String> getEnvironmentVarsFromJsonSchema(MavenProject project, Map<String, String> envVars) {
        Map<String, String> ret = new TreeMap<>();
        JsonSchema schema = getEnvironmentVariableJsonSchema(project, envVars);
        Map<String, JsonSchemaProperty> properties = schema.getProperties();
        Set<Map.Entry<String, JsonSchemaProperty>> entries = properties.entrySet();
        for (Map.Entry<String, JsonSchemaProperty> entry : entries) {
            String name = entry.getKey();
            String value = entry.getValue().getDefaultValue();
            ret.put(name, value != null ? value : "");
        }
        return ret;
    }

    private static JsonSchema getEnvironmentVariableJsonSchema(MavenProject project, Map<String, String> envVars) {
        try {
            JsonSchema schema = ExternalEnvVarHandler.loadEnvironmentSchemas(MavenUtil.getCompileClassLoader(project),
                                                                             project.getBuild().getOutputDirectory());
            if (schema == null) {
                schema = new JsonSchema();
            }
            ExternalEnvVarHandler.addEnvironmentVariables(schema, envVars);
            return schema;
        } catch (IOException exp) {
            throw new IllegalArgumentException("Cannot load environment variables from JSON schema",exp);
        }
    }

    /**
     * Finds all of the environment json schemas and combines them together
     */
    private static JsonSchema loadEnvironmentSchemas(ClassLoader classLoader, String... folderPaths) throws IOException {
        JsonSchema answer = null;
        Enumeration<URL> resources = classLoader.getResources(ENVIRONMENT_SCHEMA_FILE);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            JsonSchema schema = loadSchema(url);
            answer = combineSchemas(answer, schema);
        }
        for (String folderPath : folderPaths) {
            File file = new File(folderPath, ENVIRONMENT_SCHEMA_FILE);
            if (file.isFile()) {
                JsonSchema schema = loadSchema(file);
                answer = combineSchemas(answer, schema);
            }
        }
        return answer;
    }

    /**
     * Modifies the given json schema adding the additional environment variable overrides which either create
     * new properties or override the default values of existing known properties
     */
    private static void addEnvironmentVariables(JsonSchema schema, Map<String, String> environmentVariables) {
        Map<String, JsonSchemaProperty> properties = schema.getProperties();
        if (properties == null) {
            properties = new HashMap<>();
            schema.setProperties(properties);
        }

        Set<Map.Entry<String, String>> entries = environmentVariables.entrySet();
        for (Map.Entry<String, String> entry : entries) {
            String name = entry.getKey();
            String value = entry.getValue();
            JsonSchemaProperty property = properties.get(name);
            if (property == null) {
                property = new JsonSchemaProperty();
                properties.put(name, property);
            }
            property.setDefaultValue(value);
        }

    }

    private static JsonSchema combineSchemas(JsonSchema schema1, JsonSchema schema2) {
        if (schema1 == null) {
            return schema2;
        }
        if (schema2 != null) {
            Map<String, JsonSchemaProperty> properties2 = schema2.getProperties();
            Map<String, JsonSchemaProperty> properties1 = schema1.getProperties();
            if (properties2 != null) {
                if (properties1 == null) {
                    return schema2;
                } else {
                    properties1.putAll(properties2);
                }
            }
        }
        return schema1;
    }

    private static JsonSchema loadSchema(URL url) throws IOException {
        return objectMapper.readerFor(JsonSchema.class).readValue(url);
    }

    private static JsonSchema loadSchema(File file) throws IOException {
        return objectMapper.readerFor(JsonSchema.class).readValue(file);
    }

    /**
     * Creates a configured Jackson object mapper for parsing JSON
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }
}
