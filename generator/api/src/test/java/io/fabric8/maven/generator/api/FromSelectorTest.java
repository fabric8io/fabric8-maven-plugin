package io.fabric8.maven.generator.api;
/*
 *
 * Copyright 2016 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.docker.util.Logger;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.fabric8.maven.core.config.OpenShiftBuildStrategy.*;
import static io.fabric8.maven.core.config.PlatformMode.*;
import static org.junit.Assert.assertEquals;

/**
 * @author roland
 * @since 12/08/16
 */
@RunWith(JMockit.class)
public class FromSelectorTest {

    @Mocked
    MavenProject project;

    @Mocked
    Plugin plugin;

    @Mocked
    Logger logger;

    @Test
    public void simple() {
        final Object[] data = new Object[] {
            openshift, s2i, "1.2.3.redhat-00009", "redhat-s2i-prop",
            openshift, docker, "1.2.3.redhat-00009", "redhat-docker-prop",
            openshift, s2i, "1.2.3.fuse-00009", "redhat-s2i-prop",
            openshift, docker, "1.2.3.fuse-00009", "redhat-docker-prop",
            openshift, s2i, "1.2.3.foo-00009", "s2i-prop",
            openshift, docker, "1.2.3.foo-00009", "docker-prop",
            openshift, s2i, "1.2.3", "s2i-prop",
            openshift, docker, "1.2.3", "docker-prop",
            null, s2i, "1.2.3.redhat-00009", "redhat-docker-prop",
            null, docker, "1.2.3.redhat-00009", "redhat-docker-prop",
            null, s2i, "1.2.3.fuse-00009", "redhat-docker-prop",
            null, docker, "1.2.3.fuse-00009", "redhat-docker-prop",
            null, s2i, "1.2.3.foo-00009", "docker-prop",
            null, docker, "1.2.3.foo-00009", "docker-prop",
            null, s2i, "1.2.3", "docker-prop",
            null, docker, "1.2.3", "docker-prop",
            openshift, null, "1.2.3.redhat-00009", "redhat-docker-prop",
            openshift, null, "1.2.3.fuse-00009", "redhat-docker-prop",
            openshift, null, "1.2.3.foo-00009", "docker-prop",
            openshift, null, "1.2.3", "docker-prop",
        };

        for (int i = 0; i < data.length; i += 4) {
            MavenGeneratorContext ctx = new MavenGeneratorContext(project, null, null, new ProcessorConfig(), "fabric8:testing", logger,
                                                                  (PlatformMode) data[i], (OpenShiftBuildStrategy) data[i + 1]);

            final String version = (String) data[i + 2];
            new Expectations() {{
               plugin.getVersion(); result = version;
            }};

            FromSelector selector = new FromSelector.Default(ctx, "test");

            assertEquals(data[i + 3], selector.getFrom());
        }
    }
}
