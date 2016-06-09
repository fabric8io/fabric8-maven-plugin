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
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.ServiceNames;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.maven.core.util.KubernetesAnnotations;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;
import io.fabric8.maven.enricher.api.Kinds;
import io.fabric8.utils.Strings;
import io.fabric8.utils.URLUtils;
import org.apache.maven.project.MavenProject;

import java.util.Collections;
import java.util.Map;

import static io.fabric8.maven.core.util.MavenUtil.hasClass;

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
        // lets see if we can find grafana
        KubernetesClient kubernetes = getKubernetes();
        String ns = kubernetes.getNamespace();
        String url = KubernetesHelper.getServiceURL(kubernetes, ServiceNames.GRAFANA, ns, "http", true);
        MavenProject project = getProject();
        if (url != null) {
            String defaultDashboard = detectDefaultDashboard(project);
            String query = "?var-namespace=" + ns;

            String projectName = null;
            String version = null;

            // TODO - use the docker names which may differ from project metadata!
            if (Strings.isNullOrBlank(projectName)) {
                projectName = project.getArtifactId();
            }
            if (Strings.isNullOrBlank(version)) {
                version = project.getVersion();
            }

            if (Strings.isNotBlank(projectName)) {
                query += "&var-project=" + projectName;
            }
            if (Strings.isNotBlank(version)) {
                query += "&var-version=" + version;
            }
            return URLUtils.pathJoin(url, "dashboard/file", defaultDashboard, query);
        }
        return null;
    }

    protected String detectDefaultDashboard(MavenProject project) {
        if (hasClass(project, "org.apache.camel.CamelContext")) {
            return "camel-routes.json";
        }
        return "kubernetes-pods.json";
    }

}
