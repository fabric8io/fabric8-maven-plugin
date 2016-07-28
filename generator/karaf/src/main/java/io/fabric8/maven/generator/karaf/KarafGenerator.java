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
package io.fabric8.maven.generator.karaf;

import java.util.ArrayList;
import java.util.List;

import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.generator.api.BaseGenerator;
import io.fabric8.maven.generator.api.MavenGeneratorContext;
import io.fabric8.utils.Strings;
import org.apache.maven.project.MavenProject;

public class KarafGenerator extends BaseGenerator {
    public KarafGenerator(MavenGeneratorContext context) {
        super(context, "karaf");
    }

    private enum Config implements Configs.Key {
        combine        {{ d = "false"; }},
        name           {{ d = "%g/%a:%l"; }},
        alias          {{ d = "karaf"; }},
        baseImage      {{ d = "fabric8/s2i-karaf:1.3"; }},
        baseDir        {{ d = "/deployments/"; }},
        user           {{ d = "jboss:jboss:jboss"; }},
        cmd            {{ d = "/deployments/deploy-and-run.sh"; }},
        webPort        {{ d = "8181"; }},
        jolokiaPort    {{ d = "8778"; }};

        public String def() { return d; } protected String d;
    }

    @Override
    public List<ImageConfiguration> customize(List<ImageConfiguration> configs) {
        if (isApplicable() && shouldIncludeDefaultImage(configs)) {
            ImageConfiguration.Builder imageBuilder = new ImageConfiguration.Builder();
            BuildImageConfiguration.Builder buildBuilder = new BuildImageConfiguration.Builder()
                .assembly(createAssembly())
                .from(getConfig(Config.baseImage))
                .ports(extractPorts())
                .cmd(getConfig(Config.cmd));
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

    @Override
    public boolean isApplicable() {
        MavenProject project = getProject();
        return MavenUtil.hasPlugin(project, "org.apache.karaf.tooling:karaf-maven-plugin");
    }

    private boolean shouldIncludeDefaultImage(List<ImageConfiguration> configs) {
        boolean combineEnabled = Configs.asBoolean(getConfig(Config.combine));
        return !containsBuildConfiguration(configs) || combineEnabled;
    }

    private List<String> extractPorts() {
        // TODO would rock to look at the base image and find the exposed ports!
        List<String> answer = new ArrayList<>();
        addPortIfValid(answer, getConfig(Config.webPort));
        addPortIfValid(answer, getConfig(Config.jolokiaPort));
        return answer;
    }

    private void addPortIfValid(List<String> list, String port) {
        if (Strings.isNotBlank(port)) {
            list.add(port);
        }
    }

    private AssemblyConfiguration createAssembly() {
        return new AssemblyConfiguration.Builder()
            .basedir(getConfig(Config.baseDir))
            .user(getConfig(Config.user))
            .descriptorRef("karaf")
            .build();
    }
}
