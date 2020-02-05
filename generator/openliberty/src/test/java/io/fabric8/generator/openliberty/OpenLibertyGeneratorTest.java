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
package io.fabric8.generator.openliberty;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.jar.Attributes;

import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.Test;

import io.fabric8.maven.generator.javaexec.FatJarDetector;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.generator.api.GeneratorContext;
import mockit.Expectations;
import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;

public class OpenLibertyGeneratorTest {

	@Mocked
	Logger log;
	@Mocked
	private GeneratorContext context;

	@Mocked
	private MavenProject project;

	@Mocked
	private Build build;

	@Test
	public void testLibertyRunnable() throws MojoExecutionException, IOException {

		new MockFatJarDetector(true);

		OpenLibertyGenerator generator = new OpenLibertyGenerator(createGeneratorContext());

		System.out.println("gen: " + generator);
		generator.addAssembly(new AssemblyConfiguration.Builder());
		assertTrue("The LIBERTY_RUNNABLE_JAR env var should be set",
				generator.getEnv(false).containsKey(OpenLibertyGenerator.LIBERTY_RUNNABLE_JAR));
		assertTrue("The JAVA_APP_DIR env var should be set",
				generator.getEnv(false).containsKey(OpenLibertyGenerator.JAVA_APP_JAR));

	}

	@Test
	public void testExtractPorts() throws IOException, MojoExecutionException {

		OpenLibertyGenerator generator = new OpenLibertyGenerator(createGeneratorContext());
		List<String> ports = generator.extractPorts();
		assertNotNull(ports);
		assertTrue("The list of ports should contain 9080", ports.contains("9080"));

	}

	private GeneratorContext createGeneratorContext() throws IOException {
		new Expectations() {
			{
				context.getProject();
				result = project;
				project.getBuild();
				result = build;
				project.getBasedir();
				minTimes = 0;
				result = "basedirectory";

				String tempDir = Files.createTempDirectory("openliberty-test-project").toFile().getAbsolutePath();

				build.getDirectory();
				result = tempDir;
				build.getOutputDirectory();
				result = tempDir;
				project.getPlugin(anyString);
				result = null;
				minTimes = 0;
				project.getBuildPlugins();
				result = null;
				project.getVersion();
				result = "1.0.0";
				minTimes = 0;
			}
		};
		return context;

	}

	public static class MockFatJarDetector extends MockUp<FatJarDetector> {

		private final boolean findClass;

		public MockFatJarDetector(boolean findClass) {
			this.findClass = findClass;
		}

		@Mock
		FatJarDetector.Result scan(Invocation invocation) {

			if (!findClass) {
				return null;
			}

			FatJarDetector detector = invocation.getInvokedInstance();
			return detector.new Result(new File("/the/archive/file"), OpenLibertyGenerator.LIBERTY_SELF_EXTRACTOR,
					new Attributes());
		}

	}

}
