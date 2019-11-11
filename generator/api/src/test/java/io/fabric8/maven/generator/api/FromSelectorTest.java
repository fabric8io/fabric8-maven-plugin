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

import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.RuntimeMode;
import mockit.Expectations;
import mockit.Mocked;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
    GeneratorContext ctx;

    @Test
    public void Java8BaseImage() {
        final Object[] data = new Object[] {
            openshift, s2i, "1.2.3.redhat-00009", "redhat-s2i-prop", "redhat-istag-prop",
            openshift, docker, "1.2.3.redhat-00009", "redhat-docker-prop", "redhat-istag-prop",
            openshift, s2i, "1.2.3.fuse-00009", "redhat-s2i-prop", "redhat-istag-prop",
            openshift, docker, "1.2.3.fuse-00009", "redhat-docker-prop", "redhat-istag-prop",
            openshift, s2i, "1.2.3.foo-00009", "s2i-prop", "istag-prop",
            openshift, docker, "1.2.3.foo-00009", "docker-prop-8", "istag-prop",
            openshift, s2i, "1.2.3", "s2i-prop", "istag-prop",
            openshift, docker, "1.2.3", "docker-prop-8", "istag-prop",
            null, s2i, "1.2.3.redhat-00009", "redhat-docker-prop", "redhat-istag-prop",
            null, docker, "1.2.3.redhat-00009", "redhat-docker-prop", "redhat-istag-prop",
            null, s2i, "1.2.3.fuse-00009", "redhat-docker-prop", "redhat-istag-prop",
            null, docker, "1.2.3.fuse-00009", "redhat-docker-prop", "redhat-istag-prop",
            null, s2i, "1.2.3.foo-00009", "docker-prop-8", "istag-prop",
            null, docker, "1.2.3.foo-00009", "docker-prop-8", "istag-prop",
            null, s2i, "1.2.3", "docker-prop-8", "istag-prop",
            null, docker, "1.2.3", "docker-prop-8", "istag-prop",
            openshift, null, "1.2.3.redhat-00009", "redhat-docker-prop", "redhat-istag-prop",
            openshift, null, "1.2.3.fuse-00009", "redhat-docker-prop", "redhat-istag-prop",
            openshift, null, "1.2.3.foo-00009", "docker-prop-8", "istag-prop",
            openshift, null, "1.2.3", "docker-prop-8", "istag-prop"
        };

        for (int i = 0; i < data.length; i += 5) {
            prepareExpectionJava8Release((String) data[i + 2], (RuntimeMode) data[i], (OpenShiftBuildStrategy) data[i + 1]);
            doAssertJava8(i, data, ctx);
        }

        for (int i = 0; i < data.length; i += 5) {
            prepareExpectionJava8Target((String) data[i + 2], (RuntimeMode) data[i], (OpenShiftBuildStrategy) data[i + 1]);
            doAssertJava8(i, data, ctx);
        }
    }

    private static void doAssertJava8(int i, Object[] data, GeneratorContext ctx) {
        FromSelector selector = new FromSelector.Default(ctx, "test");

        assertEquals(data[i + 3], selector.getFrom());
        Map<String, String> fromExt = selector.getImageStreamTagFromExt();
        assertEquals(fromExt.size(),3);
        assertEquals(fromExt.get(SourceStrategy.kind.key()), "ImageStreamTag");
        assertEquals(fromExt.get(SourceStrategy.namespace.key()), "openshift");
        assertEquals(fromExt.get(SourceStrategy.name.key()), data[i + 4]);
    }

    @Test
    public void Java11BaseImage() {
        final Object[] data = new Object[] {
                openshift, docker, "1.2.3.foo-00009", "docker-prop-11",
                openshift, docker, "1.2.3", "docker-prop-11",
                null, s2i, "1.2.3.foo-00009", "docker-prop-11",
                null, docker, "1.2.3.foo-00009", "docker-prop-11",
                openshift, null, "1.2.3.foo-00009", "docker-prop-11",
                openshift, null, "1.2.3", "docker-prop-11",
        };

        for (int i = 0; i < data.length; i += 4) {
            prepareExpectionJava11Release((String) data[i + 2], (RuntimeMode) data[i], (OpenShiftBuildStrategy) data[i + 1]);
            doAssertJava11(i, data, ctx);
        }

        for (int i = 0; i < data.length; i += 4) {
            prepareExpectionJava11Target((String) data[i + 2], (RuntimeMode) data[i], (OpenShiftBuildStrategy) data[i + 1]);
            doAssertJava11(i, data, ctx);
        }
    }

    private static void doAssertJava11(int i, Object[] data, GeneratorContext ctx) {
        FromSelector selector = new FromSelector.Default(ctx, "test");
        assertEquals(data[i + 3], selector.getFrom());
    }

    private Expectations prepareExpectionJava8Release(final String version, final RuntimeMode mode, final OpenShiftBuildStrategy strategy) {
        return prepareExpectation(version, mode, strategy, String.valueOf(8), "release");
    }

    private Expectations prepareExpectionJava8Target(final String version, final RuntimeMode mode, final OpenShiftBuildStrategy strategy) {
        return prepareExpectation(version, mode, strategy, String.valueOf(8), "target");
    }

    private Expectations prepareExpectionJava11Release(final String version, final RuntimeMode mode, final OpenShiftBuildStrategy strategy) {
        return prepareExpectation(version, mode, strategy, String.valueOf(11), "release");
    }

    private Expectations prepareExpectionJava11Target(final String version, final RuntimeMode mode, final OpenShiftBuildStrategy strategy) {
        return prepareExpectation(version, mode, strategy, String.valueOf(11), "target");
    }

    private Expectations prepareExpectation(final String version, final RuntimeMode mode, final OpenShiftBuildStrategy strategy, final String javaVersion, String property) {
        return new Expectations() {{
            ctx.getProject(); result = project;

            Xpp3Dom child = new Xpp3Dom(property);
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
}