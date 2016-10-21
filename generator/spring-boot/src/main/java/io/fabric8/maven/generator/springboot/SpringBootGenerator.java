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
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.core.util.SpringBootUtil;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.generator.api.MavenGeneratorContext;
import io.fabric8.maven.generator.api.support.JavaRunGenerator;
import io.fabric8.utils.IOHelpers;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

import static io.fabric8.maven.core.util.SpringBootProperties.DEV_TOOLS_REMOTE_SECRET;
import static io.fabric8.maven.generator.api.support.JavaRunGenerator.Config.fatJar;

/**
 * @author roland
 * @since 15/05/16
 */
public class SpringBootGenerator extends JavaRunGenerator {

    public static final String SPRING_BOOT_MAVEN_PLUGIN_GA = "org.springframework.boot:spring-boot-maven-plugin";
    private Boolean springBootRepackage;

    public enum Config implements Configs.Key {
        color;

        public String def() { return d; } protected String d;
    }

    public SpringBootGenerator(MavenGeneratorContext context) {
        super(context, "spring-boot");
    }

    @Override
    public boolean isApplicable(List<ImageConfiguration> configs) {
        return shouldAddDefaultImage(configs) &&
                MavenUtil.hasPlugin(getProject(), SPRING_BOOT_MAVEN_PLUGIN_GA) &&
                // if we don't use spring boot repackaging then lets use regular JavaExecGenerator
                // and pass in a mainClass etc
                isSpringBootRepackage();
    }

    @Override
    public List<ImageConfiguration> customize(List<ImageConfiguration> configs) throws MojoExecutionException {
        if (getContext().isWatchMode()) {
            generateSpringDevToolsToken();
            addDevToolsJar(configs);
        }
        return super.customize(configs);
    }

    private void generateSpringDevToolsToken() throws MojoExecutionException {
        Properties properties = SpringBootUtil.getSpringBootApplicationProperties(getProject());
        String remoteSecret = properties.getProperty(DEV_TOOLS_REMOTE_SECRET);
        if (Strings.isNullOrEmpty(remoteSecret)) {
            String newToken = UUID.randomUUID().toString();
            log.verbose("Generating the spring devtools token in property: " + DEV_TOOLS_REMOTE_SECRET);

            File file = new File(getProject().getBasedir(), "target/classes/application.properties");
            file.getParentFile().mkdirs();
            String text = "# lets configure the spring devtools remote secret\nspring.devtools.remote.secret=" + newToken + "\n";

            if (file.exists()) {
                text = "\n" + text;
            }
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.append(text);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to append to file: " + file + ". " + e, e);
            }
        }
    }

    private void addDevToolsJar(List<ImageConfiguration> configs) throws MojoExecutionException {
        if (Objects.equals("fabric8:resource", getContext().getGoalName()) && isFatJarWithNoDependencies()) {
            MavenProject project = getProject();
            File basedir = project.getBasedir();
            File outputFile = new File(basedir, "target/classes/BOOT-INF/lib/spring-devtools.jar");
            outputFile.getParentFile().mkdirs();

            String resourceName = "fabric8-spring-devtools/spring-boot-devtools.jar";
            URL resource = getClass().getClassLoader().getResource(resourceName);
            if (resource == null) {
                throw new MojoExecutionException("Could not find resource " + resourceName + " on the classpath!");
            }
            try {
                IOHelpers.copy(resource.openStream(), new FileOutputStream(outputFile));
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to copy " + resource + " to temp file " + outputFile + ". " + e, e);
            }
        }
    }

    @Override
    protected Map<String, String> getEnv() {
        Map<String, String> ret = super.getEnv();
        if (getConfig(Config.color) != null) {
            ret.put("JAVA_OPTIONS","-Dspring.output.ansi.enabled=" + getConfig(Config.color));
        }
        return ret;
    }

    @Override
    protected boolean isFatJarWithNoDependencies() {
        String fatJarConfig = getConfig(fatJar);
        if (Strings.isNullOrEmpty(fatJarConfig)) {
            boolean springBootRepackage = isSpringBootRepackage();
            if (springBootRepackage) {
                return true;
            }
        }
        return super.isFatJarWithNoDependencies();
    }

    protected boolean isSpringBootRepackage() {
        if (springBootRepackage == null) {
            springBootRepackage = false;
            MavenProject project = getProject();
            if (project != null) {
                Plugin plugin = project.getPlugin(SPRING_BOOT_MAVEN_PLUGIN_GA);
                if (plugin != null) {
                    Map<String, PluginExecution> executionsAsMap = plugin.getExecutionsAsMap();
                    if (executionsAsMap != null) {
                        for (PluginExecution execution : executionsAsMap.values()) {
                            List<String> goals = execution.getGoals();
                            if (goals.contains("repackage")) {
                                springBootRepackage = true;
                                log.info("Using fat jar packaging as the spring boot plugin is using `repackage` goal execution");
                                break;
                            }
                        }
                    }
                }
            }
        }
        return springBootRepackage;
    }
}
