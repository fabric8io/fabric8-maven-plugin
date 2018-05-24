package io.fabric8.maven.enricher.fabric8;

import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.enricher.api.AbstractHealthCheckEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;

import static io.fabric8.maven.core.util.MavenUtil.hasDependency;

/**
 * Enriches wildfly-swarm containers with health checks if the monitoring fraction is present.
 */
public class WildFlySwarmHealthCheckEnricher extends AbstractHealthCheckEnricher {

    public WildFlySwarmHealthCheckEnricher(EnricherContext buildContext) {
        super(buildContext, "wildfly-swarm-health-check");
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
        Probe probe = discoverWildFlySwarmHealthCheck(10);
        return probe;
    }

    @Override
    protected Probe getLivenessProbe() {
        Probe probe = discoverWildFlySwarmHealthCheck(180);
        return probe;
    }

    private Probe discoverWildFlySwarmHealthCheck(int initialDelay) {
        if (hasDependency(this.getProject(), "org.wildfly.swarm", "monitor")
                || hasDependency(this.getProject(), "org.wildfly.swarm", "microprofile-health")) {
            Integer port = getPort();
            // scheme must be in upper case in k8s
            String scheme = getScheme().toUpperCase();
            String path = getPath();

            // lets default to adding a wildfly swarm health check
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
