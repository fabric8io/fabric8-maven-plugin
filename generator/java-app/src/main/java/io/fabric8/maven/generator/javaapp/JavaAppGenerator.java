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

package io.fabric8.maven.generator.javaapp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.generator.api.BaseGenerator;
import io.fabric8.maven.generator.api.MavenGeneratorContext;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.utils.Strings;
import org.apache.maven.project.MavenProject;

/**
 */
public class JavaAppGenerator extends BaseGenerator {

    public JavaAppGenerator(MavenGeneratorContext context) {
        super(context, "java.app");
    }

    private enum Config implements Configs.Key {
        mainClass {{ d = ""; }},
        combine {{ d = "false"; }},
        name    {{ d = "%g/%a:%l"; }},
        alias   {{ d = "javaapp"; }},
        from    {{ d = "fabric8/java-alpine-openjdk8-jdk"; }},
        webPort   {{ d = ""; }},
        jolokiaPort   {{ d = "8778"; }},
        prometheusPort   {{ d = "9779"; }};

        public String def() { return d; } protected String d;
    }

    @Override
    public List<ImageConfiguration> customize(List<ImageConfiguration> configs) {
        if (isApplicable() && shouldIncludeDefaultImage(configs)) {
            Map<String, String> envVars = new HashMap<>();
            String mainClass = getConfig(Config.mainClass);
            if (Strings.isNotBlank(mainClass)) {
                envVars.put(EnvironmentVariables.JAVA_MAIN_CLASS, mainClass);
            }

            ImageConfiguration.Builder imageBuilder = new ImageConfiguration.Builder();
            BuildImageConfiguration.Builder buildBuilder = new BuildImageConfiguration.Builder()
                .assembly(createAssembly())
                .from(getConfig(Config.from))
                .ports(extractPorts())
                .env(envVars);
            addLatestTagIfSnapshot(buildBuilder);
            imageBuilder
                .name(getConfig(Config.name))
                .alias(getConfig(Config.alias))
                .buildConfig(buildBuilder.build());
            configs.add(imageBuilder.build());
            return configs;
        } else {
            return configs;
        }
    }

    private boolean shouldIncludeDefaultImage(List<ImageConfiguration> configs) {
        boolean combineEnabled = Configs.asBoolean(getConfig(Config.combine));
        return !containsBuildConfiguration(configs) || combineEnabled;
    }

    private void addLatestTagIfSnapshot(BuildImageConfiguration.Builder buildBuilder) {
        MavenProject project = getProject();
        if (project.getVersion().endsWith("-SNAPSHOT")) {
            buildBuilder.tags(Collections.singletonList("latest"));
        }
    }

    private List<String> extractPorts() {
        // TODO would rock to look at the base image and find the exposed ports!
        List<String> answer = new ArrayList<>();
        addPortIfValid(answer, getConfig(Config.webPort));
        addPortIfValid(answer, getConfig(Config.jolokiaPort));
        addPortIfValid(answer, getConfig(Config.prometheusPort));
        return answer;
    }

    private void addPortIfValid(List<String> list, String port) {
        if (Strings.isNotBlank(port)) {
            list.add(port);
        }
    }

    private AssemblyConfiguration createAssembly() {
        return
            new AssemblyConfiguration.Builder()
                .basedir("/app")
                .descriptorRef("java-app")
                .build();
    }

    private boolean containsBuildConfiguration(List<ImageConfiguration> configs) {
        for (ImageConfiguration config : configs) {
            if (config.getBuildConfiguration() != null) {
                return true;
            }
        }
        return false;
    }

    protected boolean isApplicable() {
        String mainClass = getConfig(Config.mainClass);
        return Strings.isNotBlank(mainClass);
    }
}
