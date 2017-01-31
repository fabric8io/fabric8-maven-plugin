package io.fabric8.maven.enricher.fabric8;

import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.enricher.api.AbstractHealthCheckEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;

/**
 * Configures the health checks for a Vert.x project. Unlike other enricher this enricher extract the configuration from
 * the following project properties: `vertx.health.port`, `vertx.health.path`.
 * <p>
 * It builds a liveness probe and a readiness probe using:
 * <p>
 * <ul>
 * <li>`vertx.health.port` - the port, 8080 by default, a negative number disables the health check</li>
 * <li>`vertx.health.path` - the path, / by default, an empty (non null) value disables the health check</li>
 * <li>`vertx.health.scheme` - the scheme, HTTP by default, can be set to HTTPS (adjusts the port accordingly)</li>
 * </ul>
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class VertxHealthCheckEnricher extends AbstractHealthCheckEnricher {

    private static final String VERTX_MAVEN_PLUGIN_GA = "io.fabric8:vertx-maven-plugin";
    private static final String VERTX_GROUPID = "io.vertx";

    private static final int DEFAULT_MANAGEMENT_PORT = 8080;
    private static final String SCHEME_HTTP = "HTTP";

    private static final int READINESS_INITIAL_DELAY = 10;

    private static final int LIVENESS_INITIAL_DELAY = 180;

    /**
     * The project property to configure the Vert.x health check scheme.
     */
    public static final String VERTX_HEALTH_SCHEME = "vertx.health.scheme";

    /**
     * The project property to configure the Vert.x health check path.
     */
    public static final String VERTX_HEALTH_PATH = "vertx.health.path";

    /**
     * The project property to configure the Vert.x readiness health check path.
     */
    public static final String VERTX_READINESS_HEALTH_PATH = "vertx.health.readiness.path";

    /**
     * The project property to configure the Vert.x health check port.
     */
    public static final String VERTX_HEALTH_PORT = "vertx.health.port";

    // Available configuration keys
    protected enum Config implements Configs.Key {

        scheme {{
            d = SCHEME_HTTP;
        }},
        port {{
            d = Integer.toString(DEFAULT_MANAGEMENT_PORT);
        }},
        path {{
            d = null;
        }},
        readiness {{
            d = null;
        }};

        protected String d;

        public String def() {
            return d;
        }
    }

    public VertxHealthCheckEnricher(EnricherContext buildContext) {
        super(buildContext, "vertx-health-check");
    }

    @Override
    protected Probe getReadinessProbe() {
        return discoverVertxHealthCheck(READINESS_INITIAL_DELAY, true);
    }

    @Override
    protected Probe getLivenessProbe() {
        return discoverVertxHealthCheck(LIVENESS_INITIAL_DELAY, false);
    }

    private boolean isApplicable() {
        return MavenUtil.hasPlugin(getProject(), VERTX_MAVEN_PLUGIN_GA)
                || MavenUtil.hasDependencyOnAnyArtifactOfGroup(getProject(), VERTX_GROUPID);
    }

    private Probe discoverVertxHealthCheck(int initialDelay, boolean readiness) {
        if (!isApplicable()) {
            return null;
        }

        int port = getPort();

        String path = null;
        if (readiness) {
            path = getReadinessPath();
            if (path != null  && path.isEmpty()) {
                // Disabled.
                return null;
            }
        }

        if (path == null) {
            path = getPath();
        }

        if (port <= 0  || path == null  || path.isEmpty()) {
            // Health check disabled
            return null;
        }

        String scheme = getScheme();

        return new ProbeBuilder()
                .withNewHttpGet()
                .withScheme(scheme)
                .withNewPort(port)
                .withPath(path)
                .endHttpGet()
                .withInitialDelaySeconds(initialDelay).build();
    }

    private String getScheme() {
        String scheme = getContext().getProject().getProperties()
                .getProperty(VERTX_HEALTH_SCHEME);

        if (scheme != null && !scheme.trim().isEmpty()) {
            return scheme.trim();
        }

        return Configs.asString(getConfig(VertxHealthCheckEnricher.Config.scheme));
    }

    private int getPort() {
        String portAsString = getContext().getProject().getProperties()
                .getProperty(VERTX_HEALTH_PORT);

        if (portAsString != null && !portAsString.trim().isEmpty()) {
            try {
                int port = Integer.valueOf(portAsString.trim());
                if (port <= 0) {
                    return -1;
                } else {
                    return port;
                }
            } catch (NumberFormatException e) {
                // Invalid value, disable the check
                log.warn("Invalid value for `" + VERTX_HEALTH_PORT + "` - integer expected, disabling health checks");
                return -1;
            }
        }
        return Configs.asInt(getConfig(VertxHealthCheckEnricher.Config.port));
    }

    private String processPath(String path) {
        if (path != null) {
            path = path.trim();
            if (path.isEmpty()) {
                return "";
            } else {
                if (! path.startsWith("/")) {
                    path = "/" + path;
                }

                return path;
            }
        }
        return null;
    }

    private String getPath() {
        String path = getContext().getProject().getProperties()
                .getProperty(VERTX_HEALTH_PATH);

        path = processPath(path);
        if (path != null) {
            return path;
        }

        path = Configs.asString(getConfig(VertxHealthCheckEnricher.Config.path));

        return processPath(path);
    }

    private String getReadinessPath() {
        String path = getContext().getProject().getProperties()
                .getProperty(VERTX_READINESS_HEALTH_PATH);

        path = processPath(path);
        if (path != null) {
            return path;
        }

        path = Configs.asString(getConfig(Config.readiness));
        return processPath(path);
    }

}
