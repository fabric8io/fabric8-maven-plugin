package io.fabric8.maven.enricher.fabric8;

import java.util.Properties;

import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.maven.core.util.SpringBootProperties;
import io.fabric8.maven.enricher.api.EnricherContext;

import org.junit.Test;
import org.junit.runner.RunWith;

import mockit.Mocked;
import mockit.integration.junit4.JMockit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Check various configurations for spring-boot health checks
 *
 * @author nicola
 */
@RunWith(JMockit.class)
public class SpringBootHealthCheckEnricherTest {

    @Mocked
    private EnricherContext context;

    @Test
    public void testZeroConfig() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("/health", probe.getHttpGet().getPath());
        assertEquals(8080, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testWithServerPort() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(SpringBootProperties.SERVER_PORT, "8282");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("/health", probe.getHttpGet().getPath());
        assertEquals(8282, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testWithServerPortAndManagementPort() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(SpringBootProperties.SERVER_PORT, "8282");
        props.put(SpringBootProperties.MANAGEMENT_PORT, "8383");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("/health", probe.getHttpGet().getPath());
        assertEquals(8383, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testWithServerPortAndManagementPortAndServerContextPath() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(SpringBootProperties.SERVER_PORT, "8282");
        props.put(SpringBootProperties.MANAGEMENT_PORT, "8383");
        props.put(SpringBootProperties.SERVER_CONTEXT_PATH, "/p1");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("/health", probe.getHttpGet().getPath());
        assertEquals(8383, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testWithServerPortAndManagementPortAndManagementContextPath() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(SpringBootProperties.SERVER_PORT, "8282");
        props.put(SpringBootProperties.MANAGEMENT_PORT, "8383");
        props.put(SpringBootProperties.SERVER_CONTEXT_PATH, "/p1");
        props.put(SpringBootProperties.MANAGEMENT_CONTEXT_PATH, "/p2");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("/p2/health", probe.getHttpGet().getPath());
        assertEquals(8383, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testWithServerPortAndManagementPortAndManagementContextPathAndServletPath() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(SpringBootProperties.SERVER_PORT, "8282");
        props.put(SpringBootProperties.MANAGEMENT_PORT, "8383");
        props.put(SpringBootProperties.SERVER_CONTEXT_PATH, "/p1");
        props.put(SpringBootProperties.MANAGEMENT_CONTEXT_PATH, "/p2");
        props.put(SpringBootProperties.SERVLET_PATH, "/servlet");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("/p2/health", probe.getHttpGet().getPath());
        assertEquals(8383, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testWithServerPortAndManagementContextPath() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(SpringBootProperties.SERVER_PORT, "8282");
        props.put(SpringBootProperties.MANAGEMENT_CONTEXT_PATH, "/p1");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("/p1/health", probe.getHttpGet().getPath());
        assertEquals(8282, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testWithServerPortAndServerContextPathAndManagementContextPath() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(SpringBootProperties.SERVER_PORT, "8282");
        props.put(SpringBootProperties.MANAGEMENT_CONTEXT_PATH, "/p1");
        props.put(SpringBootProperties.SERVER_CONTEXT_PATH, "/p2");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("/p2/p1/health", probe.getHttpGet().getPath());
        assertEquals(8282, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testWithServerPortAndServletPath() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(SpringBootProperties.SERVER_PORT, "8282");
        props.put(SpringBootProperties.SERVLET_PATH, "/servlet");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("/servlet/health", probe.getHttpGet().getPath());
        assertEquals(8282, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testWithServerPortAndManagementContextPathAndServletPath() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(SpringBootProperties.SERVER_PORT, "8282");
        props.put(SpringBootProperties.SERVLET_PATH, "/servlet");
        props.put(SpringBootProperties.MANAGEMENT_CONTEXT_PATH, "/p1");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("/servlet/p1/health", probe.getHttpGet().getPath());
        assertEquals(8282, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testWithServerPortAndServerContextPathAndManagementContextPathAndServletPath() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(SpringBootProperties.SERVER_PORT, "8282");
        props.put(SpringBootProperties.SERVLET_PATH, "/servlet");
        props.put(SpringBootProperties.MANAGEMENT_CONTEXT_PATH, "/p1");
        props.put(SpringBootProperties.SERVER_CONTEXT_PATH, "/p2");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("/p2/servlet/p1/health", probe.getHttpGet().getPath());
        assertEquals(8282, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testSchemeWithServerPort() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(SpringBootProperties.SERVER_PORT, "8443");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("HTTP", probe.getHttpGet().getScheme());
        assertEquals(8443, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testSchemeWithServerPortAndManagementKeystore() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(SpringBootProperties.SERVER_PORT, "8080");
        props.put(SpringBootProperties.MANAGEMENT_KEYSTORE, "classpath:keystore.p12");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("HTTP", probe.getHttpGet().getScheme());
        assertEquals(8080, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testSchemeWithServerPortAndServerKeystore() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(SpringBootProperties.SERVER_PORT, "8443");
        props.put(SpringBootProperties.SERVER_KEYSTORE, "classpath:keystore.p12");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("HTTPS", probe.getHttpGet().getScheme());
        assertEquals(8443, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testSchemeWithServerPortAndManagementPortAndServerKeystore() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(SpringBootProperties.SERVER_PORT, "8443");
        props.put(SpringBootProperties.MANAGEMENT_PORT, "8081");
        props.put(SpringBootProperties.SERVER_KEYSTORE, "classpath:keystore.p12");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("HTTP", probe.getHttpGet().getScheme());
        assertEquals(8081, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testSchemeWithServerPortAndManagementPortAndManagementKeystore() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(SpringBootProperties.SERVER_PORT, "8080");
        props.put(SpringBootProperties.MANAGEMENT_PORT, "8443");
        props.put(SpringBootProperties.MANAGEMENT_KEYSTORE, "classpath:keystore.p12");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("HTTPS", probe.getHttpGet().getScheme());
        assertEquals(8443, probe.getHttpGet().getPort().getIntVal().intValue());
    }

}