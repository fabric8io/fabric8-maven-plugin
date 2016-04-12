package io.fabric8.maven.fabric8.enricher;
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

import java.util.*;

import io.fabric8.maven.fabric8.util.MavenBuildContext;
import io.fabric8.maven.fabric8.util.PluginServiceFactory;


/**
 * @author roland
 * @since 08/04/16
 */
public class EnricherManager {

    // List of enrichers used for customizing the generated deployment descriptors
    private List<Enricher> enrichers;

    public EnricherManager(MavenBuildContext buildContext) {
        enrichers = PluginServiceFactory.createServiceObjects(buildContext,
                                                              "META-INF/fabric8-enricher-default", "META-INF/fabric8-enricher");
        Collections.reverse(enrichers);
    }


    public Map<String, String> extractLabels(Kind kind) {
        Map <String, String> ret = new HashMap<>();
        for (Enricher enricher : enrichers) {
            putAllIfNotNull(ret, enricher.getLabels(kind));
        }
        return ret;
    }

    private void putAllIfNotNull(Map<String, String> ret, Map<String, String> labels) {
        if (labels != null) {
            ret.putAll(labels);
        }
    }
}
