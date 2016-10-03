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

package io.fabric8.maven.generator.springboot;

import com.google.common.base.Strings;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.generator.api.MavenGeneratorContext;
import io.fabric8.maven.generator.api.support.JavaRunGenerator;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;

import java.util.List;
import java.util.Map;

import static io.fabric8.maven.generator.api.support.JavaRunGenerator.Config.fatJar;

/**
 * @author roland
 * @since 15/05/16
 */
public class SpringBootGenerator extends JavaRunGenerator {

    public static final String SPRING_BOOT_MAVEN_PLUGIN_GA = "org.springframework.boot:spring-boot-maven-plugin";

    public SpringBootGenerator(MavenGeneratorContext context) {
        super(context, "spring-boot");
    }

    @Override
    public boolean isApplicable(List<ImageConfiguration> configs) {
        return shouldAddDefaultImage(configs) &&
                MavenUtil.hasPlugin(getProject(), SPRING_BOOT_MAVEN_PLUGIN_GA);
    }

    @Override
    protected boolean isFatJarWithNoDependencies() {
        String fatJarConfig = getConfig(fatJar);
        if (Strings.isNullOrEmpty(fatJarConfig)) {
            MavenProject project = getProject();
            if (project != null) {
                Plugin plugin = project.getPlugin(SPRING_BOOT_MAVEN_PLUGIN_GA);
                if (plugin != null) {
                    Map<String, PluginExecution> executionsAsMap = plugin.getExecutionsAsMap();
                    if (executionsAsMap != null) {
                        for (PluginExecution execution : executionsAsMap.values()) {
                            List<String> goals = execution.getGoals();
                            if (goals.contains("repackage")) {
                                log.info("Using fat jar packaging as the spring boot plugin is using `repackage` goal execution");
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return super.isFatJarWithNoDependencies();
    }
}
