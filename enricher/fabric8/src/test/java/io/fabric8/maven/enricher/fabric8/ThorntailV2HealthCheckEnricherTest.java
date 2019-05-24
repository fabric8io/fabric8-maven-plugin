/**
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
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
