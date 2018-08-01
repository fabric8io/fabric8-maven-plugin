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
package io.fabric8.maven.enricher.fabric8;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MapUtil;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;
import org.apache.commons.lang3.StringUtils;


public class PrometheusEnricher extends BaseEnricher {

    static final String ANNOTATION_PROMETHEUS_PORT = "prometheus.io/port";
    static final String ANNOTATION_PROMETHEUS_SCRAPE = "prometheus.io/scrape";

    static final String ENRICHER_NAME = "f8-prometheus";
    static final String PROMETHEUS_PORT = "9779";

    private enum Config implements Configs.Key {
        prometheusPort;

        public String def() { return d; } protected String d;
    }

    public PrometheusEnricher(EnricherContext buildContext) {
        super(buildContext, ENRICHER_NAME);
    }

    @Override
    public Map<String, String> getAnnotations(Kind kind) {
        if (kind == Kind.SERVICE) {
            String prometheusPort = findPrometheusPort();
            if (StringUtils.isNotBlank(prometheusPort)) {
                log.verbose("Add prometheus.io annotations: %s=%s, %s=%s",
                    ANNOTATION_PROMETHEUS_SCRAPE, "true",
                    ANNOTATION_PROMETHEUS_PORT, prometheusPort);

                Map<String, String> annotations = new HashMap<>();
                MapUtil.putIfAbsent(annotations, ANNOTATION_PROMETHEUS_PORT, prometheusPort);
                MapUtil.putIfAbsent(annotations, ANNOTATION_PROMETHEUS_SCRAPE, "true");
                return annotations;
            }
        }

        return super.getAnnotations(kind);
    }

    private String findPrometheusPort() {
        String prometheusPort = getConfig(Config.prometheusPort);
        if (StringUtils.isBlank(prometheusPort)) {
            for (ImageConfiguration configuration : getImages()) {
                BuildImageConfiguration buildImageConfiguration = configuration.getBuildConfiguration();
                if (buildImageConfiguration != null) {
                    List<String> ports = buildImageConfiguration.getPorts();
                    if (ports != null && ports.contains(PROMETHEUS_PORT)) {
                        prometheusPort = PROMETHEUS_PORT;
                        break;
                    }
                }
            }
        }

        return prometheusPort;
    }
}
