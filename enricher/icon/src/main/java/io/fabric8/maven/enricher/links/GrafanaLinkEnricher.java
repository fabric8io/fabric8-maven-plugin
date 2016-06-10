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
import io.fabric8.maven.core.util.Configs;
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

    private enum Config implements Configs.Key {
        metricsDashboard;

        public String def() { return d; } protected String d;
    }


    @Override
    public Map<String, String> getAnnotations(Kind kind) {
        if (Kinds.isDeployOrReplicaKind(kind)) {
            String url = findGrafanaLink();
            return url != null ? Collections.singletonMap(Annotations.Builds.METRICS_PATH, url) : null;
        } else {
            return null;
        }
    }

    private String findGrafanaLink() {
        MavenProject project = getProject();
        String defaultDashboard = detectDefaultDashboard(project);
        String query = "";
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
        if (query.startsWith("&")) {
            query = "?" + query.substring(1);
        }
        return URLUtils.pathJoin("dashboard/file", defaultDashboard, query);
    }

    protected String detectDefaultDashboard(MavenProject project) {
        String dashboard = getConfig(Config.metricsDashboard);
        if (Strings.isNotBlank(dashboard)) {
            return dashboard;
        }
        if (hasClass(project, "org.apache.camel.CamelContext")) {
            return "camel-routes.json";
        }
        return "kubernetes-pods.json";
    }

}
