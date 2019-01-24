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
package io.fabric8.maven.enricher.api.util;

import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.enricher.api.Enricher;
import io.fabric8.maven.enricher.api.Kind;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.fabric8.maven.core.util.MapUtil.putAllIfNotNull;
import static io.fabric8.maven.enricher.api.util.Misc.filterEnrichers;

public class ExtractorUtil {

    // Simple extractors
    enum Extractor {
        LABEL_EXTRACTOR {
            public Map<String, String> extract(Enricher enricher, Kind kind) {
                return enricher.getLabels(kind);
            }
        },
        ANNOTATION_EXTRACTOR {
            public Map<String, String> extract(Enricher enricher, Kind kind) {
                return enricher.getAnnotations(kind);
            }
        },
        SELECTOR_EXTRACTOR {
            public Map<String, String> extract(Enricher enricher, Kind kind) {
                return enricher.getSelector(kind);
            }
        };
        abstract Map<String, String> extract(Enricher enricher, Kind kind);
    }

    /**
     * Get all labels from all enrichers for a certain kind
     *
     * @param kind resource type for which labels should be extracted
     * @return extracted labels
     */
    public static Map<String, String> extractLabels(ProcessorConfig config, Kind kind, List<Enricher> enrichers) {
        return extract(config, Extractor.LABEL_EXTRACTOR, kind, enrichers);
    }

    public static Map<String, String> extractAnnotations(ProcessorConfig config, Kind kind, List<Enricher> enrichers) {
        return extract(config, Extractor.ANNOTATION_EXTRACTOR, kind, enrichers);
    }

    public static Map<String, String> extractSelector(ProcessorConfig config, Kind kind, List<Enricher> enrichers) {
        return extract(config, Extractor.SELECTOR_EXTRACTOR, kind, enrichers);
    }

    private static Map<String, String> extract(ProcessorConfig config, Extractor extractor, Kind kind, List<Enricher> enrichers) {
        Map <String, String> ret = new HashMap<>();
        for (Enricher enricher : filterEnrichers(config, enrichers)) {
            putAllIfNotNull(ret, extractor.extract(enricher, kind));
        }
        return ret;
    }

}
