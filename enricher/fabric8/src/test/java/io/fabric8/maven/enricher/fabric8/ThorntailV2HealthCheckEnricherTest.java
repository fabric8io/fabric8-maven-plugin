package io.fabric8.maven.enricher.fabric8;

import io.fabric8.maven.enricher.api.MavenEnricherContext;
import io.fabric8.maven.enricher.api.util.ProjectClassLoaders;
import java.net.URLClassLoader;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ThorntailV2HealthCheckEnricherTest {

    @Mocked
    protected MavenEnricherContext context;

    private void setupExpectations() {
        new Expectations() {{
            context.getProjectClassLoaders();
            result = new ProjectClassLoaders((URLClassLoader) ThorntailV2HealthCheckEnricherTest.class.getClassLoader());
        }};
    }

    @Test
    public void configureThorntailHealthPort() {

        setupExpectations();
        final ThorntailV2HealthCheckEnricher thorntailV2HealthCheckEnricher = new ThorntailV2HealthCheckEnricher(context);
        final int port = thorntailV2HealthCheckEnricher.getPort();
        assertEquals(8082, port);

    }

}
