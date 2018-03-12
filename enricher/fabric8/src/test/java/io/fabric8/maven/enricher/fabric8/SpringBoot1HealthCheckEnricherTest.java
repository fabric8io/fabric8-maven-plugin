package io.fabric8.maven.enricher.fabric8;

import mockit.integration.junit4.JMockit;
import org.junit.runner.RunWith;

@RunWith(JMockit.class)
public class SpringBoot1HealthCheckEnricherTest extends AbstractSpringBootHealthCheckEnricherSupport {

    @Override
    protected String getSpringBootVersion() {
        return "1.5.10.RELEASE";
    }
}
