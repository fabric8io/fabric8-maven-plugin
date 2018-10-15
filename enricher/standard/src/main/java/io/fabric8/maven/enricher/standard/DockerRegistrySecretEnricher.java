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
package io.fabric8.maven.enricher.standard;

import io.fabric8.maven.core.model.Configuration;
import io.fabric8.maven.core.util.SecretConstants;
import io.fabric8.maven.enricher.api.MavenEnricherContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.json.JSONObject;

public class DockerRegistrySecretEnricher extends SecretEnricher {
    final private static String ANNOTATION_KEY = "maven.fabric8.io/dockerServerId";
    final private static String ENRICHER_NAME = "fmp-docker-registry-secret";


    public DockerRegistrySecretEnricher(MavenEnricherContext buildContext) {
        super(buildContext, ENRICHER_NAME);
    }

    @Override
    protected String getAnnotationKey() {
        return ANNOTATION_KEY;
    }

    @Override
    protected Map<String, String> generateData(String dockerId) {
        final Configuration config = getContext().getConfiguration();
        final Optional<Map<String,Object>> secretConfig = config.getSecretConfiguration(dockerId);
        if (!secretConfig.isPresent()) {
            return null;
        }

        Map<String, String> params = new HashMap<>();
        for (String key : new String[] { "username", "password", "email" }) {
            if  (secretConfig.get().containsKey(key)) {
                params.put(key, secretConfig.get().get(key).toString());
            }
        }

        return Collections.singletonMap(
            SecretConstants.DOCKER_DATA_KEY,
            encode(new JSONObject().put(dockerId, params).toString()));
    }
}

