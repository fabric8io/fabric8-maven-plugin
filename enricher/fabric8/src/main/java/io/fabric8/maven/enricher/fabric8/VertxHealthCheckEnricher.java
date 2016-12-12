package io.fabric8.maven.enricher.fabric8;

import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.enricher.api.AbstractHealthCheckEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;

import java.util.Properties;

/**
 * Configures the health checks for a Vert.x project. Unlike other enricher this enricher extract the configuration from
 * the following project properties: `vertx.health.port`, `vertx.health.path`.
 *
 * It builds a liveness probe and a readiness probe using:
 *
 * <ul>
 *     <li>`vertx.health.port` - the port, 8080 by default, a negative number disables the health check</li>
 *     <li>`vertx.health.path` - the path, / by default, an empty (non null) value disables the health check</li>
 *     <li>`vertx.health.scheme` - the scheme, HTTP by default, can be set to HTTPS (adjusts the port accordingly)</li>
 * </ul>
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class VertxHealthCheckEnricher extends AbstractHealthCheckEnricher {

    private static final String VERTX_MAVEN_PLUGIN_GA = "io.fabric8:vertx-maven-plugin";
    private static final String VERTX_GROUPID = "io.vertx";

    private static final int DEFAULT_MANAGEMENT_PORT = 8080;
    private static final String SCHEME_HTTP = "HTTP";

    public VertxHealthCheckEnricher(EnricherContext buildContext) {
        super(buildContext, "vertx-health-check");
    }

    @Override
    protected Probe getReadinessProbe() {
        return discoverVertxHealthCheck(10);
    }

    @Override
    protected Probe getLivenessProbe() {
        return discoverVertxHealthCheck(180);
    }

    private boolean isApplicable() {
        return MavenUtil.hasPlugin(getProject(), VERTX_MAVEN_PLUGIN_GA)
                || MavenUtil.hasDependency(getProject(), VERTX_GROUPID);
    }

    private Probe discoverVertxHealthCheck(int initialDelay) {
        if (! isApplicable()) {
            return null;
        }

        Properties properties = getContext().getProject().getProperties();
        String healthCheckPort = properties.getProperty("vertx.health.port");
        String healthCheckPath = properties.getProperty("vertx.health.path");
        String healthCheckScheme = properties.getProperty("vertx.health.scheme", SCHEME_HTTP).trim();


        if (healthCheckPath != null && healthCheckPath.trim().isEmpty()) {
            // Health check disabled
            return null;
        }

        int port = DEFAULT_MANAGEMENT_PORT;
        if (healthCheckPort != null) {
            if (healthCheckPort.trim().isEmpty()) {
                // Health check disabled
                return null;
            }
            port = Integer.valueOf(healthCheckPort.trim());
            if (port < 0) {
                // Health check disabled
                return null;
            }
        }

        String path = "/";
        if (healthCheckPath != null) {
            path = healthCheckPath.trim();
        }


        return new ProbeBuilder().
                withNewHttpGet()
                    .withScheme(healthCheckScheme)
                    .withNewPort(port)
                    .withPath(path)
                .endHttpGet().
                withInitialDelaySeconds(initialDelay).build();
    }

}
