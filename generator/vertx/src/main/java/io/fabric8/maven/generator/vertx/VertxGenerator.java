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

package io.fabric8.maven.generator.vertx;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.generator.api.GeneratorContext;
import io.fabric8.maven.generator.javaexec.JavaExecGenerator;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import static io.fabric8.maven.generator.vertx.Constants.CLUSTER_MANAGER_SPI;
import static io.fabric8.maven.generator.vertx.Constants.SHADE_PLUGIN_GA;
import static io.fabric8.maven.generator.vertx.Constants.VERTX_DROPWIZARD;
import static io.fabric8.maven.generator.vertx.Constants.VERTX_GROUPID;
import static io.fabric8.maven.generator.vertx.Constants.VERTX_INFINIPAN;
import static io.fabric8.maven.generator.vertx.Constants.VERTX_MAVEN_PLUGIN_GA;

/**
 * Vert.x Generator.
 * <p>
 * Then main part of creating the base image is taken java-exec generator.
 * <p>
 *
 * It detects whether or not it's a Vert.x project by looking if there is the vertx-core dependency associated by the
 * Maven Shader Plugin or if the project use the Vert.x Maven Plugin.
 *
 * To avoid the issue to write file in the current working directory the `cacheDirBase` is configured to `/tmp`.
 *
 * When a cluster manager is detected in the classpath, `-cluster` is automatically appended to the command line.
 *
 * To avoid DNS resolution issue, the async DNS resolver is disabled (falling back to the regular Java resolver)
 *
 * If vertx-dropwizard-metrics is in the classpath, the metrics are enabled and the JMX export is also enabled.
 */
public class VertxGenerator extends JavaExecGenerator {

  public VertxGenerator(GeneratorContext context) {
    super(context, "vertx");
  }

  @Override
  public boolean isApplicable(List<ImageConfiguration> configs) throws MojoExecutionException {
    return shouldAddImageConfiguration(configs)
        && (MavenUtil.hasPlugin(getProject(), VERTX_MAVEN_PLUGIN_GA)
        || MavenUtil.hasDependencyOnAnyArtifactOfGroup(getProject(), VERTX_GROUPID));
  }

  @Override
  protected List<String> getExtraJavaOptions() {
    List<String> opts = super.getExtraJavaOptions();
    opts.add("-Dvertx.cacheDirBase=/tmp");

    if (! contains("-Dvertx.disableDnsResolver=", opts)) {
      opts.add("-Dvertx.disableDnsResolver=true");
    }

    if (MavenUtil.hasDependency(getProject(), VERTX_GROUPID, VERTX_DROPWIZARD)) {
      opts.add("-Dvertx.metrics.options.enabled=true");
      opts.add("-Dvertx.metrics.options.jmxEnabled=true");
      opts.add("-Dvertx.metrics.options.jmxDomain=vertx");
    }

    if (! contains("-Djava.net.preferIPv4Stack", opts)  && MavenUtil.hasDependency(getProject(), VERTX_GROUPID, VERTX_INFINIPAN)) {
      opts.add("-Djava.net.preferIPv4Stack=true");
    }

    return opts;
  }

  @Override
  protected Map<String, String> getEnv(boolean prePackagePhase) throws MojoExecutionException {
    Map<String, String> map = super.getEnv(prePackagePhase);

    String args = map.get("JAVA_ARGS");
    if (args == null) {
      args = "";
    }

    if (MavenUtil.hasResource(getProject(), CLUSTER_MANAGER_SPI)) {
      if (! args.isEmpty()) {
        args += " ";
      }
      args += "-cluster";
    }

    if (! args.isEmpty()) {
      map.put("JAVA_ARGS", args);
    }
    return map;
  }

  private boolean contains(String prefix, List<String> opts) {
    return opts.stream().anyMatch(val -> val.startsWith(prefix));
  }

  @Override
  protected boolean isFatJar() throws MojoExecutionException {
    return !hasMainClass() && isUsingFatJarPlugin() || super.isFatJar();
  }

  private boolean isUsingFatJarPlugin() {
    MavenProject project = getProject();
    Plugin shade = project.getPlugin(SHADE_PLUGIN_GA);
    Plugin vertx = project.getPlugin(VERTX_MAVEN_PLUGIN_GA);
    return shade != null || vertx != null;
  }

  @Override
  protected List<String> extractPorts() {
    Map<String, Integer> extractedPorts = new VertxPortsExtractor(log).extract(getProject());
    List<String> ports = new ArrayList<>();
    for (Integer p : extractedPorts.values()) {
      ports.add(String.valueOf(p));
    }
    // If there are no specific vertx ports found, we reuse the ports from the JavaExecGenerator
    return ports.size() > 0 ? ports : super.extractPorts();
  }
}
