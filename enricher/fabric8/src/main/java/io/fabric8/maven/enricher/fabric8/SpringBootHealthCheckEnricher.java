package io.fabric8.maven.enricher.fabric8;

import java.util.Properties;

import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.maven.core.util.SpringBootProperties;
import io.fabric8.maven.core.util.SpringBootUtil;
import io.fabric8.maven.enricher.api.AbstractHealthCheckEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;

import static io.fabric8.maven.core.util.MavenUtil.hasClass;
import static io.fabric8.utils.PropertiesHelper.getInteger;

/**
 * Enriches spring-boot containers with health checks if the actuator module is present.
 */
public class SpringBootHealthCheckEnricher extends AbstractHealthCheckEnricher {

    private static final int DEFAULT_MANAGEMENT_PORT = 8080;

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
            if (hasClass(this.getProject(), "org.springframework.boot.actuate.health.HealthIndicator")) {
                Properties properties = SpringBootUtil.getSpringBootApplicationProperties(this.getProject());
                Integer port = getInteger(properties, SpringBootProperties.MANAGEMENT_PORT, getInteger(properties, SpringBootProperties.SERVER_PORT, DEFAULT_MANAGEMENT_PORT));

                // lets default to adding a spring boot actuator health check
                return new ProbeBuilder().withNewHttpGet().
                        withNewPort(port).withPath("/health").endHttpGet().withInitialDelaySeconds(initialDelay).build();
            }
        } catch (Exception ex) {
            log.error("Error while reading the spring-boot configuration", ex);
        }
        return null;
    }

}
