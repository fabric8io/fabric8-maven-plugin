/*
 * Copyright 2017 Red Hat, Inc.
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

package io.fabric8.maven.generator.springboot;

import io.fabric8.maven.core.util.SpringBootProperties;
import io.fabric8.maven.generator.api.GeneratorContext;
import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * @author Charles Moulliard
 * @since 19/)&/2017
 */
@RunWith(JMockit.class)
public class SpringBootGeneratorServerPortTest {

    @Mocked
    private GeneratorContext context;

    @Mocked
    private MavenProject project;

    @Mocked
    private Build build;

    @Mocked
    private Model model;

    @Test
    @Ignore
    public void checkPortValue() throws Exception {
        SpringBootGenerator generator = new SpringBootGenerator(createGeneratorWithYamlfileContext());

        Properties props = new Properties();
        props.setProperty("server.port","9090");
        assertEquals("9090",props.get(SpringBootProperties.SERVER_PORT));

        List<String> ports = generator.extractPorts();
        assertEquals(9090,ports.get(0));
    }

    @Test
    public void checkPortFromMavenPropertyValue() throws Exception {
        SpringBootGenerator generator = new SpringBootGenerator(createGeneratorWithMavenPropertiesContext());
        List<String> ports = generator.extractPorts();
        assertEquals(9090,ports.get(0));
    }

    private GeneratorContext createGeneratorWithYamlfileContext() throws Exception {
        new Expectations() {{
            context.getProject(); result = project;
            project.getBuild(); result = build;
            String tempDir = Files.createTempDirectory("springboot-test-project").toFile().getAbsolutePath();
            build.getDirectory(); result = tempDir;
            build.getOutputDirectory(); result = tempDir;
            URL url = SpringBootGeneratorServerPortTest.class.getResource("/application.yml");
            FileUtils.copyFileToDirectory(new File(url.toURI()),new File(tempDir));
        }};
        return context;
    }

    private GeneratorContext createGeneratorWithMavenPropertiesContext() throws Exception {
        new Expectations() {{
            context.getProject(); result = project;
            project.getBuild(); result = build;
            String tempDir = Files.createTempDirectory("springboot-test-project").toFile().getAbsolutePath();
            build.getDirectory(); result = tempDir;
            build.getOutputDirectory(); result = tempDir;

            // Pass the property to the maven model
            // Method of the parent ModelBase is well called and invoked
            Deencapsulation.invoke(model,"addProperty","server.port","7777");
            Deencapsulation.setField(model,"name", "Spring-Boot Demo");
            Deencapsulation.setField(project,"model", model);
            Deencapsulation.setField(context,"project",project);
            // NPE is raised here
            model.getProperties().get("server.port"); result = "7777";
        }};
        return context;
    }
}
