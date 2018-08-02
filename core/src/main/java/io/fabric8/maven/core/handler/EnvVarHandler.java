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

package io.fabric8.maven.core.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.maven.core.extenvvar.ExternalEnvVarHandler;
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

    List<EnvVar> getEnvironmentVariables(Map<String, String> envVars)  {

        List<EnvVar> ret = new ArrayList<>();

        Map<String, String> envs = ExternalEnvVarHandler.getExportedEnvironmentVariables(project, envVars);
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

}
