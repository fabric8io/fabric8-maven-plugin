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
package io.fabric8.maven.generator.javaexec;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;

import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.generator.api.FromSelector;
import io.fabric8.maven.generator.api.GeneratorContext;
import mockit.Expectations;
import mockit.Mocked;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author roland
 * @since 22/09/16
 */
public class JavaRunGeneratorTest {

    @Mocked
    GeneratorContext ctx;

    @Mocked
    MavenProject project;

    @Mocked
    Plugin plugin;

    @Test
    public void fromSelector() throws IOException {
        Object[] data = {
            "3.1.123", PlatformMode.kubernetes, null, "java.upstream.docker",
            "3.1.redhat-101", PlatformMode.kubernetes, null, "java.redhat.docker",
            "3.1.123", PlatformMode.openshift, OpenShiftBuildStrategy.docker, "java.upstream.docker",
            "3.1.redhat-101", PlatformMode.openshift, OpenShiftBuildStrategy.docker, "java.redhat.docker",
            "3.1.123", PlatformMode.openshift, OpenShiftBuildStrategy.s2i, "java.upstream.s2i",
            "3.1.redhat-101", PlatformMode.openshift, OpenShiftBuildStrategy.s2i, "java.redhat.s2i",
        };

        Properties imageProps = getDefaultImageProps();

        for (int i = 0; i < data.length; i += 4) {
            prepareExpectation((String) data[i], (PlatformMode) data[i+1], (OpenShiftBuildStrategy) data[i+2]);
            final GeneratorContext context = ctx;
            FromSelector selector = new FromSelector.Default(context, "java");
            String from = selector.getFrom();
            assertEquals(imageProps.getProperty((String) data[i+3]), from);
        }
    }

    private Expectations prepareExpectation(final String version, final PlatformMode mode, final OpenShiftBuildStrategy strategy) {
        return new Expectations() {{
            ctx.getProject(); result = project;
            project.getPlugin("io.fabric8:fabric8-maven-plugin"); result = plugin;
            plugin.getVersion(); result = version;
            ctx.getPlatformMode();result = mode;
            ctx.getStrategy(); result = strategy;
        }};
    }

    private Properties getDefaultImageProps() throws IOException {
        Properties props = new Properties();
        Enumeration<URL> resources = getClass().getClassLoader().getResources("META-INF/fabric8/default-images.properties");
        while (resources.hasMoreElements()) {
            props.load(resources.nextElement().openStream());
        }
        return props;
    }
}
