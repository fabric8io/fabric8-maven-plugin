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

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.maven.docker.util.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Processs YAML fragments to override parts of the YAML for OpenShift specific clusters
 */
public class OpenShiftOverrideResources {
    private final Map<KindAndName, HasMetadata> map = new HashMap<>();
    private final Logger log;

    public OpenShiftOverrideResources(Logger log) {
        this.log = log;
    }

    public void addOpenShiftOverride(HasMetadata item) {
        KindAndName key = new KindAndName(item);
        map.put(key, item);
    }


    /**
     * Applies any overrides if we have any for the given item
     */
    public HasMetadata overrideResource(HasMetadata item) {
        KindAndName key = new KindAndName(item);
        HasMetadata override = map.get(key);
        if (override != null) {
            log.info("Overriding " + key);
            HasMetadata answer = KubernetesResourceUtil.mergeResources(item, override, log, false);
            if (answer != null) {
                return answer;
            }
        }
        return item;
    }
}
