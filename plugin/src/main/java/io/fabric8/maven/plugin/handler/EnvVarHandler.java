package io.fabric8.maven.plugin.handler;
/*
 * 
 * Copyright 2016 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.util.*;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.maven.plugin.support.JsonSchema;
import io.fabric8.maven.plugin.support.JsonSchemaProperty;
import io.fabric8.maven.plugin.support.JsonSchemas;
import io.fabric8.maven.plugin.util.MavenUtils;
import org.apache.maven.project.MavenProject;

/**
 * @author roland
 * @since 08/04/16
 */
class EnvVarHandler {

    private MavenProject project;

    EnvVarHandler(MavenProject project) {
        this.project = project;
    }

    List<EnvVar> getEnvironmentVariables(Map<String, String> envVars) throws IOException {

        List<EnvVar> ret = new ArrayList<>();

        Map<String, String> envs = getExportedEnvironmentVariables(envVars);
        Map<String, EnvVar> envMap = convertToEnvVarMap(envs);
        ret.addAll(envMap.values());

        ret.add(
            new EnvVarBuilder()
                .withName("KUBERNETES_NAMESPACE")
                .withNewValueFrom()
                  .withNewFieldRef()
                     .withFieldPath("metadata.namespace")
                  .endFieldRef()
                .endValueFrom()
                .build());

        return ret;
    }

    // ==============================================================================================

    private Map<String, EnvVar> convertToEnvVarMap(Map<String, String> envs) {
        Map<String, EnvVar> envMap = new HashMap<>();
        for (Map.Entry<String, String> entry : envs.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();

            if (name != null) {
                EnvVar env = new EnvVarBuilder().withName(name).withValue(value).build();
                envMap.put(name,env);
            }
        }
        return envMap;
    }

    private Map<String, String> getExportedEnvironmentVariables(Map<String, String> envVars) throws IOException {
        Map<String, String> ret = getEnvironmentVarsFromJsonSchema(envVars);
        ret.putAll(envVars);
        return ret;
    }

    private Map<String, String> getEnvironmentVarsFromJsonSchema(Map<String, String> envVars)
        throws IOException {
        Map<String, String> ret = new TreeMap<>();
        JsonSchema schema = getEnvironmentVariableJsonSchema(envVars);
        Map<String, JsonSchemaProperty> properties = schema.getProperties();
        Set<Map.Entry<String, JsonSchemaProperty>> entries = properties.entrySet();
        for (Map.Entry<String, JsonSchemaProperty> entry : entries) {
            String name = entry.getKey();
            String value = entry.getValue().getDefaultValue();
            ret.put(name, value != null ? value : "");
        }
        return ret;
    }

    private JsonSchema getEnvironmentVariableJsonSchema(Map<String, String> envVars) throws IOException {
        JsonSchema schema = JsonSchemas.loadEnvironmentSchemas(MavenUtils.getCompileClassLoader(project),
                                                               project.getBuild().getOutputDirectory());
        if (schema == null) {
            schema = new JsonSchema();
        }
        JsonSchemas.addEnvironmentVariables(schema, envVars);
        return schema;
    }

}
