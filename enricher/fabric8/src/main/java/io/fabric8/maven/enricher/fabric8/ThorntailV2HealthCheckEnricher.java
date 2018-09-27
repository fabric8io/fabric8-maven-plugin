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
package io.fabric8.maven.enricher.fabric8;

import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.enricher.api.EnricherContext;

import static io.fabric8.maven.core.util.MavenUtil.hasDependency;

/**
 * Enriches thorntail-v2 containers with health checks if the monitoring fraction is present.
 */
public class ThorntailV2HealthCheckEnricher extends AbstractHealthCheckEnricher {

    public ThorntailV2HealthCheckEnricher(EnricherContext buildContext) {
        super(buildContext, "f8-healthcheck-thorntail-v2");
    }

    // Available configuration keys
    private enum Config implements Configs.Key {

        scheme {{
            d = "HTTP";
        }},
        port {{
            d = "8080";
        }},
        path {{
            d = "/health";
        }};

        protected String d;

        public String def() {
            return d;
        }
    }

    @Override
    protected Probe getReadinessProbe() {
        return discoverThorntailHealthCheck(10);
    }

    @Override
    protected Probe getLivenessProbe() {
        return discoverThorntailHealthCheck(180);
    }

    private Probe discoverThorntailHealthCheck(int initialDelay) {
        if (hasDependency(this.getProject(), "io.thorntail", "thorntail-kernel")) {
            // if there's thorntail-kernel, it's Thorntail v4
            return null;
        }

        if (hasDependency(this.getProject(), "io.thorntail", "monitor")
                || hasDependency(this.getProject(), "io.thorntail", "microprofile-health")) {
            Integer port = getPort();
            // scheme must be in upper case in k8s
            String scheme = getScheme().toUpperCase();
            String path = getPath();

            return new ProbeBuilder().
                    withNewHttpGet().withNewPort(port).withPath(path).withScheme(scheme).endHttpGet().
                    withInitialDelaySeconds(initialDelay).build();
        }
        return null;
    }

    protected String getScheme() {
        return Configs.asString(getConfig(Config.scheme));
    }

    protected int getPort() {
        return Configs.asInt(getConfig(Config.port));
    }

    protected String getPath() {
        return Configs.asString(getConfig(Config.path));
    }
}
