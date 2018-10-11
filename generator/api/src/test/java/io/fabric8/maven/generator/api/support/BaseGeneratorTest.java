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
package io.fabric8.maven.generator.api.support;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.generator.api.FromSelector;
import io.fabric8.maven.generator.api.GeneratorContext;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.fabric8.maven.core.config.OpenShiftBuildStrategy.SourceStrategy.kind;
import static io.fabric8.maven.core.config.OpenShiftBuildStrategy.SourceStrategy.name;
import static io.fabric8.maven.core.config.OpenShiftBuildStrategy.SourceStrategy.namespace;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author roland
 * @since 10/01/17
 */
@RunWith(JMockit.class)
public class BaseGeneratorTest {

    @Mocked
    private GeneratorContext ctx;

    @Mocked
    private MavenProject project;

    @Mocked
    private ProcessorConfig config;

    @Test
    public void fromAsConfigured() {
        final Properties projectProps = new Properties();
        projectProps.put("fabric8.generator.from","propFrom");

        setupContextKubernetes(projectProps,"configFrom", null);
        BaseGenerator generator = createGenerator(null);
        assertEquals("configFrom",generator.getFromAsConfigured());

        setupContextKubernetes(projectProps,null, null);
        generator = createGenerator(null);
        assertEquals("propFrom",generator.getFromAsConfigured());
    }

    public TestBaseGenerator createGenerator(FromSelector fromSelector) {
        return fromSelector != null ?
            new TestBaseGenerator(ctx, "test-generator", fromSelector) :
            new TestBaseGenerator(ctx, "test-generator");
    }

    @Test
    public void defaultAddFrom() {
        Properties props = new Properties();
        for (boolean isOpenShift : new Boolean[]{false, true}) {
            for (boolean isRedHat : new Boolean[]{false, true}) {
                for (TestFromSelector selector : new TestFromSelector[]{null, new TestFromSelector(ctx, isRedHat)}) {
                    for (String from : new String[]{null, "openshift/testfrom"}) {
                        setupContext(props, isOpenShift, from, null);

                        BuildImageConfiguration.Builder builder = new BuildImageConfiguration.Builder();
                        BaseGenerator generator = createGenerator(selector);
                        generator.addFrom(builder);
                        BuildImageConfiguration config = builder.build();
                        if (isRedHat && isOpenShift && selector != null) {
                            if (from != null) {
                                assertEquals("testfrom:latest", config.getFrom());
                                assertFromExt(config.getFromExt(), "testfrom:latest", "openshift");
                            } else {
                                assertEquals("selectorIstagFromRedhat", config.getFrom());
                                assertFromExt(config.getFromExt(), selector.getIstagFrom(), "openshift");
                            }
                        } else {
                            if (from != null) {
                                assertEquals(config.getFrom(), from);
                                assertNull(config.getFromExt());
                            } else {
                                System.out.println(isRedHat + " " + isOpenShift);
                                assertNull(config.getFromExt());
                                assertEquals(config.getFrom(),
                                             selector != null ?
                                                 (isOpenShift ?
                                                     selector.getS2iBuildFrom() :
                                                     selector.getDockerBuildFrom())
                                                 : null);
                            }
                        }
                    }
                }
            }
        }
    }

    public void assertFromExt(Map<String, String> fromExt, String fromName, String namespaceName) {
        assertEquals(3, fromExt.keySet().size());
        assertEquals(fromName, fromExt.get(name.key()));
        assertEquals("ImageStreamTag", fromExt.get(kind.key()));
        assertEquals(namespaceName, fromExt.get(namespace.key()));
    }

    @Test
    public void addFromIstagModeWithSelector() {
        Properties props = new Properties();
        props.put("fabric8.generator.fromMode","istag");

        for (String from : new String[] { null, "test_namespace/test_image:2.0"}) {
            setupContext(props, false, from, null);

            BuildImageConfiguration.Builder builder = new BuildImageConfiguration.Builder();
            BaseGenerator generator = createGenerator(new TestFromSelector(ctx, false));
            generator.addFrom(builder);
            BuildImageConfiguration config = builder.build();
            assertEquals(from == null ? "selectorIstagFromUpstream" : "test_image:2.0", config.getFrom());
            Map<String, String> fromExt = config.getFromExt();
            if (from != null) {
                assertFromExt(fromExt,"test_image:2.0", "test_namespace");
            } else {
                assertFromExt(fromExt, "selectorIstagFromUpstream", "openshift");
            }
        }
    }

    @Test
    public void addFromIstagModeWithoutSelector() {
        Properties props = new Properties();
        props.put("fabric8.generator.fromMode","istag");
        for (String from : new String[] { null, "test_namespace/test_image:2.0"}) {
            setupContext(props, false, from, null);

            BuildImageConfiguration.Builder builder = new BuildImageConfiguration.Builder();
            BaseGenerator generator = createGenerator(null);
            generator.addFrom(builder);
            BuildImageConfiguration config = builder.build();
            assertEquals(from == null ? null : "test_image:2.0", config.getFrom());
            Map<String, String> fromExt = config.getFromExt();
            if (from == null) {
                assertNull(fromExt);
            } else {
                assertFromExt(fromExt, "test_image:2.0", "test_namespace");
            }
        }
    }

    @Test
    public void addFromIstagWithNameWithoutTag() {
        Properties props = new Properties();
        setupContext(props, false, "test_namespace/test_image", "istag");
        BuildImageConfiguration.Builder builder = new BuildImageConfiguration.Builder();
        BaseGenerator generator = createGenerator(null);
        generator.addFrom(builder);
        BuildImageConfiguration config = builder.build();
        assertEquals("test_image:latest",config.getFrom());
    }


    @Test
    public void addFromInvalidMode() {
        try {
            Properties props = new Properties();
            setupContextKubernetes(props, null, "blub");

            BuildImageConfiguration.Builder builder = new BuildImageConfiguration.Builder();
            BaseGenerator generator = createGenerator(null);
            generator.addFrom(builder);
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains("fromMode"));
            assertTrue(exp.getMessage().contains("test-generator"));
        }
    }

    @Test
    public void shouldAddDefaultImage(@Mocked final ImageConfiguration ic1, @Mocked final ImageConfiguration ic2,
                                      @Mocked final BuildImageConfiguration bc) {
        new Expectations() {{
            ic1.getBuildConfiguration(); result = bc; minTimes = 0;
            ic2.getBuildConfiguration(); result = null; minTimes = 0;
        }};
        BaseGenerator generator = createGenerator(null);
        assertTrue(generator.shouldAddImageConfiguration(Collections.<ImageConfiguration>emptyList()));
        assertFalse(generator.shouldAddImageConfiguration(Arrays.asList(ic1, ic2)));
        assertTrue(generator.shouldAddImageConfiguration(Arrays.asList(ic2)));
        assertFalse(generator.shouldAddImageConfiguration(Arrays.asList(ic1)));
    }

    @Test
    public void addLatestTagIfSnapshot() {
        new Expectations() {{
            ctx.getProject(); result = project;
            project.getVersion(); result = "1.2-SNAPSHOT";
        }};
        BuildImageConfiguration.Builder builder = new BuildImageConfiguration.Builder();
        BaseGenerator generator = createGenerator(null);
        generator.addLatestTagIfSnapshot(builder);;
        BuildImageConfiguration config = builder.build();
        List<String> tags = config.getTags();
        assertEquals(1, tags.size());
        assertTrue(tags.get(0).endsWith("latest"));
    }

    @Test
    public void getImageName() {
        setupNameContext(null, "config_test_name");
        BaseGenerator generator = createGenerator(null);
        assertEquals("config_test_name", generator.getImageName());

        setupNameContext("prop_test_name", null);
        generator = createGenerator(null);
        assertEquals("prop_test_name", generator.getImageName());

        setupNameContext("prop_test_name", "config_test_name");
        generator = createGenerator(null);
        assertEquals("config_test_name", generator.getImageName());

        setupNameContext(null, null);
        generator = createGenerator(null);
        assertEquals("%g/%a:%l", generator.getImageName());

    }

    @Test
    public void getRegistry() {
        Properties props = new Properties();
        props.put("fabric8.generator.registry", "fabric8.io");
        setupContextKubernetes(props, null, null);

        BaseGenerator generator = createGenerator(null);
        assertEquals("fabric8.io", generator.getRegistry());
    }

    @Test
    public void getRegistryInOpenshift() {
        Properties props = new Properties();
        props.put("fabric8.generator.registry", "fabric8.io");
        props.put(PlatformMode.FABRIC8_EFFECTIVE_PLATFORM_MODE, "openshift");
        setupContextOpenShift(props, null, null);

        BaseGenerator generator = createGenerator(null);
        assertNull(generator.getRegistry());
    }

    private void setupNameContext(String propertyName, final String configName) {
        final Properties props = new Properties();
        if (propertyName != null) {
            props.put("fabric8.generator.name", propertyName);
        }
        new Expectations() {{
            ctx.getProject(); result = project;
            project.getProperties(); result = props;
            ctx.getConfig(); result = config;
            config.getConfig("test-generator", "name"); result = configName; minTimes = 0;
        }};
    }

    public void setupContext(Properties props, boolean isOpenShift, String from, String fromMode) {
        if (isOpenShift) {
            setupContextOpenShift(props, from, fromMode);
        } else {
            setupContextKubernetes(props, from, fromMode);
        }
    }

    public void setupContextKubernetes(final Properties projectProps, final String configFrom, final String configFromMode) {
        new Expectations() {{
            ctx.getProject(); result = project;
            project.getProperties(); result = projectProps;
            ctx.getConfig(); result = config;
            config.getConfig("test-generator", "from"); result = configFrom; minTimes = 0;
            config.getConfig("test-generator", "fromMode"); result = configFromMode; minTimes = 0;
            ctx.getPlatformMode();result = PlatformMode.kubernetes;minTimes = 0;
            ctx.getStrategy(); result = null; minTimes = 0;
        }};
    }
    public void setupContextOpenShift(final Properties projectProps, final String configFrom, final String configFromMode) {
        new Expectations() {{
            ctx.getProject(); result = project;
            project.getProperties(); result = projectProps;
            ctx.getConfig(); result = config;
            config.getConfig("test-generator", "from"); result = configFrom; minTimes = 0;
            config.getConfig("test-generator", "fromMode"); result = configFromMode; minTimes = 0;
            ctx.getPlatformMode();result = PlatformMode.openshift;minTimes = 0;
            ctx.getStrategy(); result = OpenShiftBuildStrategy.s2i; minTimes = 0;
        }};
    }

    private class TestBaseGenerator extends BaseGenerator {
        public TestBaseGenerator(GeneratorContext context, String name) {
            super(context, name);
        }

        public TestBaseGenerator(GeneratorContext context, String name, FromSelector fromSelector) {
            super(context, name, fromSelector);
        }

        @Override
        public boolean isApplicable(List<ImageConfiguration> configs) {
            return true;
        }

        @Override
        public List<ImageConfiguration> customize(List<ImageConfiguration> existingConfigs, boolean prePackagePhase) throws MojoExecutionException {
            return existingConfigs;
        }
    }

    private class TestFromSelector extends FromSelector {

        private boolean isRedHat;

        public TestFromSelector(GeneratorContext context, boolean isRedHat) {
            super(context);
            this.isRedHat = isRedHat;
        }

        @Override
        public boolean isRedHat() {
            return isRedHat;
        }

        @Override
        protected String getDockerBuildFrom() {
            return !isRedHat ? "selectorDockerFromUpstream" : "selectorDockerFromRedhat";
        }

        @Override
        protected String getS2iBuildFrom() {
            return !isRedHat ? "selectorS2iFromUpstream" : "selectorS2iFromRedhat";
        }

        @Override
        protected String getIstagFrom() {
            return !isRedHat ? "selectorIstagFromUpstream" : "selectorIstagFromRedhat";
        }
    }
}
