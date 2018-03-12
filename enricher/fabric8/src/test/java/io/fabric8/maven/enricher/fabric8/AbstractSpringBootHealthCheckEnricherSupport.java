package io.fabric8.maven.enricher.fabric8;

import java.util.*;

import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.maven.core.util.SpringBootConfigurationHelper;
import io.fabric8.maven.enricher.api.EnricherContext;

import mockit.Expectations;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;

import mockit.Mocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Check various configurations for spring-boot health checks
 *
 * @author nicola
 */
public abstract class AbstractSpringBootHealthCheckEnricherSupport {

    @Mocked
    protected EnricherContext context;

    protected SpringBootConfigurationHelper propertyHelper;

    @Before
    public void init() {
        String version = getSpringBootVersion();
        this.propertyHelper = new SpringBootConfigurationHelper(version);

        final MavenProject project = new MavenProject();

        Set<Artifact> artifacts = new HashSet<>();
        Artifact a = new DefaultArtifact("org.springframework.boot", "spring-boot", version, "compile", "jar", "", null);
        a.setResolved(true);
        artifacts.add(a);

        project.setArtifacts(artifacts);

        new Expectations() {{
            context.getProject(); result = project;
        }};
    }

    protected abstract String getSpringBootVersion();

    @Test
    public void testZeroConfig() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals(propertyHelper.getActuatorDefaultBasePath() + "/health", probe.getHttpGet().getPath());
        assertEquals(8080, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testWithServerPort() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(propertyHelper.getServerPortPropertyKey(), "8282");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals(propertyHelper.getActuatorDefaultBasePath() + "/health", probe.getHttpGet().getPath());
        assertEquals(8282, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testWithServerPortAndManagementPort() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(propertyHelper.getServerPortPropertyKey(), "8282");
        props.put(propertyHelper.getManagementPortPropertyKey(), "8383");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals(propertyHelper.getActuatorDefaultBasePath() + "/health", probe.getHttpGet().getPath());
        assertEquals(8383, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testWithServerPortAndManagementPortAndServerContextPath() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(propertyHelper.getServerPortPropertyKey(), "8282");
        props.put(propertyHelper.getManagementPortPropertyKey(), "8383");
        props.put(propertyHelper.getServerContextPathPropertyKey(), "/p1");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals(propertyHelper.getActuatorDefaultBasePath() + "/health", probe.getHttpGet().getPath());
        assertEquals(8383, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testWithServerPortAndManagementPortAndManagementContextPath() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(propertyHelper.getServerPortPropertyKey(), "8282");
        props.put(propertyHelper.getManagementPortPropertyKey(), "8383");
        props.put(propertyHelper.getServerContextPathPropertyKey(), "/p1");
        props.put(propertyHelper.getManagementContextPathPropertyKey(), "/p2");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("/p2" + propertyHelper.getActuatorDefaultBasePath() + "/health", probe.getHttpGet().getPath());
        assertEquals(8383, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testWithServerPortAndManagementPortAndManagementContextPathAndServletPath() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(propertyHelper.getServerPortPropertyKey(), "8282");
        props.put(propertyHelper.getManagementPortPropertyKey(), "8383");
        props.put(propertyHelper.getServerContextPathPropertyKey(), "/p1");
        props.put(propertyHelper.getManagementContextPathPropertyKey(), "/p2");
        props.put(propertyHelper.getServletPathPropertyKey(), "/servlet");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("/p2" + propertyHelper.getActuatorDefaultBasePath() + "/health", probe.getHttpGet().getPath());
        assertEquals(8383, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testWithServerPortAndManagementContextPath() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(propertyHelper.getServerPortPropertyKey(), "8282");
        props.put(propertyHelper.getManagementContextPathPropertyKey(), "/p1");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("/p1" + propertyHelper.getActuatorDefaultBasePath() +"/health", probe.getHttpGet().getPath());
        assertEquals(8282, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testWithServerPortAndServerContextPathAndManagementContextPath() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(propertyHelper.getServerPortPropertyKey(), "8282");
        props.put(propertyHelper.getManagementContextPathPropertyKey(), "/p1");
        props.put(propertyHelper.getServerContextPathPropertyKey(), "/p2");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("/p2/p1" + propertyHelper.getActuatorDefaultBasePath() + "/health", probe.getHttpGet().getPath());
        assertEquals(8282, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testWithServerPortAndServletPath() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(propertyHelper.getServerPortPropertyKey(), "8282");
        props.put(propertyHelper.getServletPathPropertyKey(), "/servlet");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("/servlet" + propertyHelper.getActuatorDefaultBasePath() + "/health", probe.getHttpGet().getPath());
        assertEquals(8282, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testWithServerPortAndManagementContextPathAndServletPath() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(propertyHelper.getServerPortPropertyKey(), "8282");
        props.put(propertyHelper.getServletPathPropertyKey(), "/servlet");
        props.put(propertyHelper.getManagementContextPathPropertyKey(), "/p1");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("/servlet/p1" + propertyHelper.getActuatorDefaultBasePath() + "/health", probe.getHttpGet().getPath());
        assertEquals(8282, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testWithServerPortAndServerContextPathAndManagementContextPathAndServletPath() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(propertyHelper.getServerPortPropertyKey(), "8282");
        props.put(propertyHelper.getServletPathPropertyKey(), "/servlet");
        props.put(propertyHelper.getManagementContextPathPropertyKey(), "/p1");
        props.put(propertyHelper.getServerContextPathPropertyKey(), "/p2");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("/p2/servlet/p1" + propertyHelper.getActuatorDefaultBasePath() + "/health", probe.getHttpGet().getPath());
        assertEquals(8282, probe.getHttpGet().getPort().getIntVal().intValue());
    }

    @Test
    public void testSchemeWithServerPort() {
        SpringBootHealthCheckEnricher enricher = new SpringBootHealthCheckEnricher(context);
        Properties props = new Properties();
        props.put(propertyHelper.getServerPortPropertyKey(), "8443");

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
        props.put(propertyHelper.getServerPortPropertyKey(), "8080");
        props.put(propertyHelper.getManagementKeystorePropertyKey(), "classpath:keystore.p12");

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
        props.put(propertyHelper.getServerPortPropertyKey(), "8443");
        props.put(propertyHelper.getServerKeystorePropertyKey(), "classpath:keystore.p12");

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
        props.put(propertyHelper.getServerPortPropertyKey(), "8443");
        props.put(propertyHelper.getManagementPortPropertyKey(), "8081");
        props.put(propertyHelper.getServerKeystorePropertyKey(), "classpath:keystore.p12");

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
        props.put(propertyHelper.getServerPortPropertyKey(), "8080");
        props.put(propertyHelper.getManagementPortPropertyKey(), "8443");
        props.put(propertyHelper.getManagementKeystorePropertyKey(), "classpath:keystore.p12");

        Probe probe = enricher.buildProbe(props, 10);
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
        assertEquals("HTTPS", probe.getHttpGet().getScheme());
        assertEquals(8443, probe.getHttpGet().getPort().getIntVal().intValue());
    }

}