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

import java.util.Map;

import io.fabric8.maven.plugin.config.KubernetesConfiguration;
import io.fabric8.maven.plugin.util.EnricherManager;
import io.fabric8.maven.enricher.api.Kind;

/**
 * @author roland
 * @since 08/04/16
 */
class LabelHandler {

    private final EnricherManager enricher;

    LabelHandler(EnricherManager enricher) {
        this.enricher = enricher;
    }

    Map<String, String> extractLabels(Kind kind, KubernetesConfiguration config) {
        Map<String, String> ret = enricher.extractLabels(kind);
        Map<String, String> configLabels = config.getLabels();
        if (configLabels != null) {
            ret.putAll(configLabels);
        }
        return ret;
    }


}
