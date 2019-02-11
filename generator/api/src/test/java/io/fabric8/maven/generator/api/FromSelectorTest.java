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
package io.fabric8.maven.generator.api;

import java.util.Map;

import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.RuntimeMode;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.docker.util.Logger;
import mockit.Expectations;
import mockit.Mocked;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.junit.Test;

import static io.fabric8.maven.core.config.OpenShiftBuildStrategy.SourceStrategy;
import static io.fabric8.maven.core.config.OpenShiftBuildStrategy.docker;
import static io.fabric8.maven.core.config.OpenShiftBuildStrategy.s2i;
import static io.fabric8.maven.core.config.RuntimeMode.openshift;
import static org.junit.Assert.assertEquals;

/**
 * @author roland
 * @since 12/08/16
 */
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
            openshift, s2i, "1.2.3.redhat-00009", "redhat-s2i-prop", "redhat-istag-prop",
            openshift, docker, "1.2.3.redhat-00009", "redhat-docker-prop", "redhat-istag-prop",
            openshift, s2i, "1.2.3.fuse-00009", "redhat-s2i-prop", "redhat-istag-prop",
            openshift, docker, "1.2.3.fuse-00009", "redhat-docker-prop", "redhat-istag-prop",
            openshift, s2i, "1.2.3.foo-00009", "s2i-prop", "istag-prop",
            openshift, docker, "1.2.3.foo-00009", "docker-prop", "istag-prop",
            openshift, s2i, "1.2.3", "s2i-prop", "istag-prop",
            openshift, docker, "1.2.3", "docker-prop", "istag-prop",
            null, s2i, "1.2.3.redhat-00009", "redhat-docker-prop", "redhat-istag-prop",
            null, docker, "1.2.3.redhat-00009", "redhat-docker-prop", "redhat-istag-prop",
            null, s2i, "1.2.3.fuse-00009", "redhat-docker-prop", "redhat-istag-prop",
            null, docker, "1.2.3.fuse-00009", "redhat-docker-prop", "redhat-istag-prop",
            null, s2i, "1.2.3.foo-00009", "docker-prop", "istag-prop",
            null, docker, "1.2.3.foo-00009", "docker-prop", "istag-prop",
            null, s2i, "1.2.3", "docker-prop", "istag-prop",
            null, docker, "1.2.3", "docker-prop", "istag-prop",
            openshift, null, "1.2.3.redhat-00009", "redhat-docker-prop", "redhat-istag-prop",
            openshift, null, "1.2.3.fuse-00009", "redhat-docker-prop", "redhat-istag-prop",
            openshift, null, "1.2.3.foo-00009", "docker-prop", "istag-prop",
            openshift, null, "1.2.3", "docker-prop", "istag-prop"
        };

        for (int i = 0; i < data.length; i += 5) {
            GeneratorContext ctx = new GeneratorContext.Builder()
                .project(project)
                .config(new ProcessorConfig())
                .logger(logger)
                .platformMode((RuntimeMode) data[i])
                .strategy((OpenShiftBuildStrategy) data[i + 1])
                .build();

            final String version = (String) data[i + 2];
            new Expectations() {{
               plugin.getVersion(); result = version;
            }};

            FromSelector selector = new FromSelector.Default(ctx, "test");

            assertEquals(data[i + 3], selector.getFrom());
            Map<String, String> fromExt = selector.getImageStreamTagFromExt();
            assertEquals(fromExt.size(),3);
            assertEquals(fromExt.get(SourceStrategy.kind.key()), "ImageStreamTag");
            assertEquals(fromExt.get(SourceStrategy.namespace.key()), "openshift");
            assertEquals(fromExt.get(SourceStrategy.name.key()), data[i + 4]);
        }
    }

}
