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

package io.fabric8.maven.plugin.generator;

import java.util.ArrayList;
import java.util.List;

import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.util.ClassUtil;
import io.fabric8.maven.core.util.PluginServiceFactory;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.generator.api.Generator;
import io.fabric8.maven.generator.api.MavenGeneratorContext;
import io.fabric8.maven.docker.config.ImageConfiguration;
import org.apache.maven.project.MavenProject;

/**
 * Manager responsible for finding and calling generators
 * @author roland
 * @since 15/05/16
 */
public class GeneratorManager {

    public static List<ImageConfiguration> generate(List<ImageConfiguration> imageConfigs,
                                                    ProcessorConfig generatorConfig,
                                                    MavenProject project,
                                                    Logger log,
                                                    PlatformMode mode,
                                                    OpenShiftBuildStrategy strategy,
                                                    boolean useProjectClasspath) {

        List<ImageConfiguration> ret = imageConfigs;

        PluginServiceFactory<MavenGeneratorContext> pluginFactory = new PluginServiceFactory<>(
            new MavenGeneratorContext(project, generatorConfig, log, mode, strategy));

        if (useProjectClasspath) {
            pluginFactory.addAdditionalClassLoader(ClassUtil.createProjectClassLoader(project, log));
        }

        List<Generator> generators =
            pluginFactory.createServiceObjects("META-INF/fabric8/generator-default",
                                               "META-INF/fabric8/fabric8-generator-default",
                                               "META-INF/fabric8/generator",
                                               "META-INF/fabric8-generator");
        generators = generatorConfig.order(generators,"generator");
        List<Generator> usableGenerators = new ArrayList<>();
        log.verbose("Generators:");
        for (Generator generator : generators) {
            if (generatorConfig.use(generator.getName())) {
                log.verbose(" - %s",generator.getName());
                usableGenerators.add(generator);
            }
        }
        for (Generator generator : usableGenerators) {
            if (generator.isApplicable()) {
                log.info("Running generator %s", generator.getName());
                ret = generator.customize(ret);
            }
        }
        return ret;
    }
}
