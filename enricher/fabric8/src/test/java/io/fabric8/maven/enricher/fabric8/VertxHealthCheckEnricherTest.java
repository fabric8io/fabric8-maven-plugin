package io.fabric8.maven.enricher.fabric8;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.enricher.api.EnricherContext;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Properties;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@RunWith(JMockit.class)
public class VertxHealthCheckEnricherTest {


    @Mocked
    private EnricherContext context;

    @Test
    public void testDefaultConfiguration() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final Properties props = new Properties();
        new Expectations() {{
            context.getProject().getProperties(); result = props;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNotNull(probe);
        assertNull(probe.getHttpGet().getHost());
        assertEquals(probe.getHttpGet().getScheme(), "HTTP");
        assertEquals(probe.getHttpGet().getPort().getIntVal().intValue(), 8080);
        assertEquals(probe.getHttpGet().getPath(), "/");

        probe = enricher.getReadinessProbe();
        assertNotNull(probe);
        assertEquals(probe.getHttpGet().getScheme(), "HTTP");
        assertNull(probe.getHttpGet().getHost());
        assertEquals(probe.getHttpGet().getPort().getIntVal().intValue(), 8080);
        assertEquals(probe.getHttpGet().getPath(), "/");
    }

    @Test
    public void testWithCustomConfigurationComingFromConf() {
        final ProcessorConfig config = new ProcessorConfig(
                null,
                null,
                Collections.singletonMap(
                        "vertx-health-check",
                        new TreeMap(
                        ImmutableMap.of(
                                VertxHealthCheckEnricher.Config.path.name(),
                                "health",
                                VertxHealthCheckEnricher.Config.port.name(),
                                "1234",
                                VertxHealthCheckEnricher.Config.scheme.name(),
                                "HTTPS"
                        ))
                )
        );

        new Expectations() {{
            context.getConfig(); result = config;
        }};

        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        Probe probe = enricher.getLivenessProbe();
        assertNotNull(probe);
        assertNull(probe.getHttpGet().getHost());
        assertEquals(probe.getHttpGet().getScheme(), "HTTPS");
        assertEquals(probe.getHttpGet().getPort().getIntVal().intValue(), 1234);
        assertEquals(probe.getHttpGet().getPath(), "/health");

        probe = enricher.getReadinessProbe();
        assertNotNull(probe);
        assertEquals(probe.getHttpGet().getScheme(), "HTTPS");
        assertNull(probe.getHttpGet().getHost());
        assertEquals(probe.getHttpGet().getPort().getIntVal().intValue(), 1234);
        assertEquals(probe.getHttpGet().getPath(), "/health");
    }

    @Test
    public void testCustomConfiguration() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final Properties props = new Properties();
        props.put("vertx.health.path", "/health");
        props.put("vertx.health.port", " 8081 ");
        props.put("vertx.health.scheme", " https");
        new Expectations() {{
            context.getProject().getProperties(); result = props;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNotNull(probe);
        assertNull(probe.getHttpGet().getHost());
        assertEquals(probe.getHttpGet().getScheme(), "https");
        assertEquals(probe.getHttpGet().getPort().getIntVal().intValue(), 8081);
        assertEquals(probe.getHttpGet().getPath(), "/health");

        probe = enricher.getReadinessProbe();
        assertNotNull(probe);
        assertEquals(probe.getHttpGet().getScheme(), "https");
        assertNull(probe.getHttpGet().getHost());
        assertEquals(probe.getHttpGet().getPort().getIntVal().intValue(), 8081);
        assertEquals(probe.getHttpGet().getPath(), "/health");
    }

    @Test
    public void testDisabledUsingEmptyPath() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final Properties props = new Properties();
        props.put("vertx.health.path", "");
        new Expectations() {{
            context.getProject().getProperties(); result = props;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNull(probe);
        probe = enricher.getReadinessProbe();
        assertNull(probe);
    }

    @Test
    public void testDisabledUsingNegativePort() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final Properties props = new Properties();
        props.put("vertx.health.port", " -1 ");
        new Expectations() {{
            context.getProject().getProperties(); result = props;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNull(probe);
        probe = enricher.getReadinessProbe();
        assertNull(probe);
    }

    @Test
    public void testDisabledUsingNegativePortUsingConfiguration() {
        final ProcessorConfig config = new ProcessorConfig(
                null,
                null,
                Collections.singletonMap(
                        "vertx-health-check",
                        new TreeMap(
                                ImmutableMap.of(
                                        VertxHealthCheckEnricher.Config.port.name(),
                                        "-1"
                                ))
                )
        );

        new Expectations() {{
            context.getConfig(); result = config;
        }};

        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        Probe probe = enricher.getLivenessProbe();
        assertNull(probe);
        probe = enricher.getReadinessProbe();
        assertNull(probe);
    }


}