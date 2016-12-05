package io.fabric8.maven.enricher.fabric8;

import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.maven.enricher.api.AbstractHealthCheckEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;

import static io.fabric8.maven.core.util.MavenUtil.hasDependency;

/**
 * Enriches wildfly-swarm containers with health checks if the monitoring fraction is present.
 */
public class WildFlySwarmHealthCheckEnricher extends AbstractHealthCheckEnricher {

    private static final int DEFAULT_HEALTH_PORT = 8080;
    private static final String SCHEME_HTTP = "HTTP";

    public WildFlySwarmHealthCheckEnricher(EnricherContext buildContext) {
        super(buildContext, "wildfly-swarm-health-check");
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
        if (hasDependency(this.getProject(), "org.wildfly.swarm", "monitor")) {
            // TODO: wildfly-swarm does not yet have a standard configuration file
            Integer port = DEFAULT_HEALTH_PORT;
            String scheme = SCHEME_HTTP;

            // lets default to adding a wildfly swarm health check
            return new ProbeBuilder().
                    withNewHttpGet().withNewPort(port).withPath("/health").withScheme(scheme).endHttpGet().
                    withInitialDelaySeconds(initialDelay).build();
        }
        return null;
    }

}
