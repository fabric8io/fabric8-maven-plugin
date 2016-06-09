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
package io.fabric8.maven.enricher.links;

import io.fabric8.kubernetes.api.Annotations;
import io.fabric8.maven.core.util.KubernetesAnnotations;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;
import io.fabric8.maven.enricher.api.Kinds;

import java.util.Collections;
import java.util.Map;

/**
 */
public class GrafanaLinkEnricher extends BaseEnricher {
    public GrafanaLinkEnricher(EnricherContext buildContext) {
          super(buildContext, "grafanaLink");
      }

      @Override
      public Map<String, String> getAnnotations(Kind kind) {
          if (Kinds.isDeployOrReplicaKind(kind)) {
              String url = findGrafanaLink();
              return url != null ? Collections.singletonMap(KubernetesAnnotations.METRICS_URL, url) : null;
          } else {
              return null;
          }
      }

    private String findGrafanaLink() {
        // TODO lets try find grafana service...
        // TODO lets add a default dashboard plus the parameters...
        return null;
    }

}
