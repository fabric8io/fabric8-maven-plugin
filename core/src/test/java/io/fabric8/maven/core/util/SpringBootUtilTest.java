/*
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
package io.fabric8.maven.core.util;

import io.fabric8.utils.PropertiesHelper;
import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.junit.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Checking the behaviour of utility methods.
 */
public class SpringBootUtilTest {

    @Test
    public void testYamlToPropertiesParsing() throws Exception {

        MavenProject project = new MavenProject();
        Build build = new Build();

        setMavenProject(project, build);

        URL testAppPropertyResource = SpringBootUtilTest.class.getResource("/util/test-application.yml");

        FileUtils.copyFile(ResourceUtils.getFile(testAppPropertyResource), new File("target/test-classes",
                "application.yml"));

        Properties props = SpringBootUtil.getApplicationProperties(project, Collections.<String>emptyList());

        assertNotEquals(0, props.size());

        assertEquals(new Integer(8081), PropertiesHelper.getInteger(props, "management.port"));
        assertEquals("jdbc:mysql://127.0.0.1:3306", props.getProperty("spring.datasource.url"));
        assertEquals("value0", props.getProperty("example.nested.items[0].value"));
        assertEquals("value1", props.getProperty("example.nested.items[1].value"));
        assertEquals("sub0", props.getProperty("example.nested.items[2].elements[0].element[0].subelement"));
        assertEquals("sub1", props.getProperty("example.nested.items[2].elements[0].element[1].subelement"));

    }

    @Test
    public void testYamlToPropertiesMerge() throws Exception {

        MavenProject project = new MavenProject();
        Build build = new Build();

        setMavenProject(project, build);

        URL testAppPropertyResource = SpringBootUtilTest.class.getResource("/util/test-application-merge-multi.yml");

        FileUtils.copyFile(ResourceUtils.getFile(testAppPropertyResource), new File("target/test-classes",
                "application.yml"), "UTF-8", null, true);

        Properties props = SpringBootUtil.getApplicationProperties(project, Collections.<String>emptyList());

        assertNotEquals(0, props.size());

        assertEquals(new Integer(9090), PropertiesHelper.getInteger(props, "server.port"));
        assertEquals("Hello", props.getProperty("my.name"));
        assertEquals("Foo", props.getProperty("their.name"));
    }

    @Test
    public void testWithDifferentConfigName() throws Exception {

        System.setProperty("spring.config.name", "foo");

        MavenProject project = new MavenProject();
        Build build = new Build();

        setMavenProject(project, build);

        URL testAppPropertyResource = SpringBootUtilTest.class.getResource("/util/test-application-named.yml");

        FileUtils.copyFile(ResourceUtils.getFile(testAppPropertyResource), new File("target/test-classes",
                "foo.yml"), "UTF-8", null, true);

        Properties props = SpringBootUtil.getApplicationProperties(project, Collections.<String>emptyList());

        assertNotEquals(0, props.size());

        assertEquals(new Integer(9090), PropertiesHelper.getInteger(props, "server.port"));
        assertEquals("Foo", props.getProperty("their.name"));

        System.getProperties().remove("spring.config.name");
    }

    @Test
    public void testPropertiesInclude() throws Exception {

        MavenProject project = new MavenProject();
        Build build = new Build();

        setMavenProject(project, build);

        URL testAppPropertyResource = SpringBootUtilTest.class.getResource("/util/test-application-include.yml");

        FileUtils.copyFile(ResourceUtils.getFile(testAppPropertyResource), new File("target/test-classes",
                "application.yml"), "UTF-8", null, true);

        SpringApplication sbBuilder = new SpringApplicationBuilder(AnnotationConfigApplicationContext.class)
                .web(false)
                .headless(true)
                .bannerMode(Banner.Mode.OFF)
                .build();

        ConfigurableApplicationContext ctx = sbBuilder.run();

        Properties props = SpringBootUtil.getApplicationProperties(project,Collections.<String>emptyList());

        assertNotEquals(0, props.size());

        assertEquals(new Integer(2020), PropertiesHelper.getInteger(props, "my.port"));
        assertEquals("bar", props.getProperty("my.name"));
        assertEquals("foo", props.getProperty("name"));
    }


    @Test
    public void testProfilePropertiesForDev() throws Exception {

        MavenProject project = new MavenProject();
        Build build = new Build();

        setMavenProject(project, build);

        URL testAppPropertyResource = SpringBootUtilTest.class.getResource("/util/test-application-multi.yml");

        FileUtils.copyFile(ResourceUtils.getFile(testAppPropertyResource), new File("target/test-classes",
                "application.yml"), "UTF-8", null, true);

        Properties props = SpringBootUtil.getApplicationProperties(project,"dev");

        assertEquals(new Integer(8080), PropertiesHelper.getInteger(props, "server.port"));
        assertEquals("Hello", props.getProperty("my.name"));
    }

    @Test
    public void testProfilePropertiesForQa() throws Exception {

        MavenProject project = new MavenProject();
        Build build = new Build();

        setMavenProject(project, build);

        URL testAppPropertyResource = SpringBootUtilTest.class.getResource("/util/test-application-multi.yml");

        FileUtils.copyFile(ResourceUtils.getFile(testAppPropertyResource), new File("target/test-classes",
                "application.yml"), "UTF-8", null, true);

        Properties props = SpringBootUtil.getApplicationProperties(project,"qa");

        assertNotEquals(0, props.size());

        assertEquals(new Integer(9090), PropertiesHelper.getInteger(props, "server.port"));
        assertEquals("Hola!", props.getProperty("their.name"));
    }

//   // @Test TODO SK Remove this after Roland Review  - this will not happen at all
//    public void testNonExistentYamlToPropertiesParsing() throws Exception {
//
//        Properties props = SpringBootUtil.getPropertiesFromYamlResource(
//                SpringBootUtilTest.class.getResource("/this-file-does-not-exist")
//                , null);
//
//        MavenProject project = new MavenProject();
//        Build build = new Build();
//
//        setMavenProject(project, build);
//
//        URL testAppPropertyResource = SpringBootUtilTest.class.getResource("/this-file-does-not-exist");
//
//        FileUtils.copyFile(ResourceUtils.getFile(testAppPropertyResource), new File("target/test-classes",
//                "application.yml"), "UTF-8", null, true);
//
//        Properties props = SpringBootUtil.getApplicationProperties(project,"qa");
//        assertNotNull(props);
//        assertEquals(0, props.size());
//
//    }

    @Test
    public void testPropertiesParsing() throws Exception {

        MavenProject project = new MavenProject();
        Build build = new Build();

        setMavenProject(project, build);

        URL testAppPropertyResource = SpringBootUtilTest.class.getResource("/util/test-application.properties");

        FileUtils.copyFile(ResourceUtils.getFile(testAppPropertyResource), new File("target/test-classes",
                "application.properties"), "UTF-8", null, true);

        Properties props = SpringBootUtil.getApplicationProperties(project,Collections.<String>emptyList());


        assertNotEquals(0, props.size());

        assertEquals(new Integer(8081), PropertiesHelper.getInteger(props, "management.port"));
        assertEquals("jdbc:mysql://127.0.0.1:3306", props.getProperty("spring.datasource.url"));
        assertEquals("value0", props.getProperty("example.nested.items[0].value"));
        assertEquals("value1", props.getProperty("example.nested.items[1].value"));

    }

//    @Test  TODO SK Remove this after Roland Review
//    public void testNonExistentPropertiesParsing() throws IOException {
//
//        Properties props = SpringBootUtil.getPropertiesResource(SpringBootUtilTest.class.getResource(
//                "/this-file-does-not-exist"), null);
//        assertNotNull(props);
//        assertEquals(0, props.size());
//    }

    public void setMavenProject(final MavenProject project, final Build build) throws IOException {
        //Set Build Dir
        final String outputTempDir = Files.createTempDirectory(UUID.randomUUID().toString()).toFile().getAbsolutePath();
        new File(outputTempDir).mkdirs();
        build.setOutputDirectory(outputTempDir);
        project.setBuild(build);
    }

}
