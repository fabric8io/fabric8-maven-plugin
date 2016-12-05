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

import java.util.Properties;

import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.maven.core.util.SpringBootProperties;
import io.fabric8.maven.core.util.SpringBootUtil;
import io.fabric8.maven.enricher.api.AbstractHealthCheckEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.utils.Strings;

import static io.fabric8.maven.core.util.MavenUtil.hasAllClasses;
import static io.fabric8.utils.PropertiesHelper.getInteger;

/**
 * Enriches spring-boot containers with health checks if the actuator module is present.
 */
public class SpringBootHealthCheckEnricher extends AbstractHealthCheckEnricher {

    private static final String[] REQUIRED_CLASSES = {
            "org.springframework.boot.actuate.health.HealthIndicator",
            "org.springframework.web.context.support.GenericWebApplicationContext"
    };

    private static final int DEFAULT_MANAGEMENT_PORT = 8080;
    private static final String SCHEME_HTTPS = "HTTPS";
    private static final String SCHEME_HTTP = "HTTP";

    public SpringBootHealthCheckEnricher(EnricherContext buildContext) {
        super(buildContext, "spring-boot-health-check");
    }

    @Override
    protected Probe getReadinessProbe() {
        Probe probe = discoverSpringBootHealthCheck(10);
        return probe;
    }

    @Override
    protected Probe getLivenessProbe() {
        Probe probe = discoverSpringBootHealthCheck(180);
        return probe;
    }

    private Probe discoverSpringBootHealthCheck(int initialDelay) {
        try {
            if (hasAllClasses(this.getProject(), REQUIRED_CLASSES)) {
                Properties properties = SpringBootUtil.getSpringBootApplicationProperties(this.getProject());
                Integer port = getInteger(properties, SpringBootProperties.MANAGEMENT_PORT, getInteger(properties, SpringBootProperties.SERVER_PORT, DEFAULT_MANAGEMENT_PORT));
                String scheme = Strings.isNotBlank(properties.getProperty(SpringBootProperties.SERVER_KEYSTORE)) ? SCHEME_HTTPS : SCHEME_HTTP;

                // lets default to adding a spring boot actuator health check
                return new ProbeBuilder().
                        withNewHttpGet().withNewPort(port).withPath("/health").withScheme(scheme).endHttpGet().
                        withInitialDelaySeconds(initialDelay).build();
            }
        } catch (Exception ex) {
            log.error("Error while reading the spring-boot configuration", ex);
        }
        return null;
    }

}
