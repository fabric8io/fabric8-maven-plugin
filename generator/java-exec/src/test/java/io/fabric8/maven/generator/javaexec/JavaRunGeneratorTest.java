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

import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.RuntimeMode;
import io.fabric8.maven.generator.api.FromSelector;
import io.fabric8.maven.generator.api.GeneratorContext;
import mockit.Expectations;
import mockit.Mocked;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

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

    @Test
    public void fromSelector() throws IOException {
        Object[] data = {
            "3.1.123", RuntimeMode.kubernetes, null, "java.8.upstream.docker",
            "3.1.redhat-101", RuntimeMode.kubernetes, null, "java.redhat.docker",
            "3.1.123", RuntimeMode.openshift, OpenShiftBuildStrategy.docker, "java.8.upstream.docker",
            "3.1.redhat-101", RuntimeMode.openshift, OpenShiftBuildStrategy.docker, "java.redhat.docker",
            "3.1.123", RuntimeMode.openshift, OpenShiftBuildStrategy.s2i, "java.upstream.s2i",
            "3.1.redhat-101", RuntimeMode.openshift, OpenShiftBuildStrategy.s2i, "java.redhat.s2i",
        };

        Properties imageProps = getDefaultImageProps();

        for (int i = 0; i < data.length; i += 4) {
            prepareExpectionJava8((String) data[i], (RuntimeMode) data[i+1], (OpenShiftBuildStrategy) data[i+2]);
            final GeneratorContext context = ctx;
            FromSelector selector = new FromSelector.Default(context, "java");
            String from = selector.getFrom();
            assertEquals(imageProps.getProperty((String) data[i+3]), from);
        }
    }

    @Test
    public void fromSelectorJava11() throws IOException {
        Object[] data = {
                "3.1.123", RuntimeMode.kubernetes, null, "java.11.upstream.docker",
                "3.1.123", RuntimeMode.openshift, OpenShiftBuildStrategy.docker, "java.11.upstream.docker"
        };

        Properties imageProps = getDefaultImageProps();

        for (int i = 0; i < data.length; i += 4) {
            prepareExpectionJava11((String) data[i], (RuntimeMode) data[i+1], (OpenShiftBuildStrategy) data[i+2]);
            final GeneratorContext context = ctx;
            FromSelector selector = new FromSelector.Default(context, "java");
            String from = selector.getFrom();
            assertEquals(imageProps.getProperty((String) data[i+3]), from);
        }
    }

    private Expectations prepareExpectionJava8(final String version, final RuntimeMode mode, final OpenShiftBuildStrategy strategy) {
        return prepareExpectation(version, mode, strategy, String.valueOf(8));
    }

    private Expectations prepareExpectionJava11(final String version, final RuntimeMode mode, final OpenShiftBuildStrategy strategy) {
        return prepareExpectation(version, mode, strategy, String.valueOf(11));
    }

    private Expectations prepareExpectation(final String version, final RuntimeMode mode, final OpenShiftBuildStrategy strategy, final String javaVersion) {
        return new Expectations() {{
            ctx.getProject(); result = project;

            Xpp3Dom child = new Xpp3Dom("release");
            child.setValue(javaVersion);
            Xpp3Dom dom = new Xpp3Dom("configuration");
            dom.addChild(child);

            Plugin plugin1 = new Plugin();
            plugin1.setArtifactId("maven-compiler-plugin");
            plugin1.setGroupId("org.apache.maven.plugins");
            plugin1.setConfiguration(dom);

            Plugin plugin2 = new Plugin();
            plugin2.setArtifactId("fabric8-maven-plugin");
            plugin2.setGroupId("io.fabric8");
            plugin2.setVersion(version);

            List<Plugin> plugins = new ArrayList<>();
            plugins.add(plugin1);
            plugins.add(plugin2);

            project.getBuildPlugins();
            result = plugins;

            project.getProperties();
            result = new Properties();

            ctx.getRuntimeMode();result = mode;
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
