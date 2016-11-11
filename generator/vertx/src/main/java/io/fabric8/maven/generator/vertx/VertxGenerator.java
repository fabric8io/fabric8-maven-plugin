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

import java.util.List;

import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.generator.api.GeneratorContext;
import io.fabric8.maven.generator.javaexec.JavaExecGenerator;
import io.fabric8.utils.Strings;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * Vert.x Generator.
 *
 * Then main part of creating the base image is taken java-exec generator.
 *
 * In addition this generator here adds an appropriate healthcheck when vertx-dropwizard-metrics is
 * given as a dependency (TODO)
 */
public class VertxGenerator extends JavaExecGenerator {

    public VertxGenerator(GeneratorContext context) {
        super(context, "vertx");
    }

    @Override
    public boolean isApplicable(List<ImageConfiguration> configs) throws MojoExecutionException {
        // Disabled for now
        return false;
    }
}
