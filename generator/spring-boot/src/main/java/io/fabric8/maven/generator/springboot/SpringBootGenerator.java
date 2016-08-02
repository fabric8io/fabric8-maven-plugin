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

import java.util.ArrayList;
import java.util.List;

import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.generator.api.BaseGenerator;
import io.fabric8.maven.generator.api.MavenGeneratorContext;
import io.fabric8.utils.Strings;
import org.apache.maven.project.MavenProject;

/**
 * @author roland
 * @since 15/05/16
 */
public class SpringBootGenerator extends BaseGenerator {

    private static final String S2I_BUILDER_IMAGE = "fabric8/s2i-java";
    private static final String BASE_IMAGE = "fabric8/java-alpine-openjdk8-jdk";


    public SpringBootGenerator(MavenGeneratorContext context) {
        super(context, "spring-boot");
    }

    private enum Config implements Configs.Key {
        combine        {{ d = "false"; }},
        name           {{ d = "%g/%a:%l"; }},
        alias          {{ d = "springboot"; }},
        from,
        webPort        {{ d = "8080"; }},
        jolokiaPort    {{ d = "8778"; }},
        prometheusPort {{ d = "9779"; }};

        public String def() { return d; } protected String d;
    }

    @Override
    public List<ImageConfiguration> customize(List<ImageConfiguration> configs) {
        if (isApplicable() && shouldIncludeDefaultImage(configs)) {
            ImageConfiguration.Builder imageBuilder = new ImageConfiguration.Builder();
            BuildImageConfiguration.Builder buildBuilder = new BuildImageConfiguration.Builder()
                .assembly(createAssembly())
                .from(getFrom())
                .ports(extractPorts());
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

    private String getFrom() {
        String from = getConfig(Config.from);
        if (from != null) {
            return from;
        }
        MavenGeneratorContext ctx = getContext();
        PlatformMode mode = ctx.getMode();
        OpenShiftBuildStrategy strategy = ctx.getStrategy();
        return mode == PlatformMode.openshift && strategy == OpenShiftBuildStrategy.s2i ?
            S2I_BUILDER_IMAGE :
            BASE_IMAGE;
    }

    @Override
    public boolean isApplicable() {
        MavenProject project = getProject();
        return MavenUtil.hasPlugin(project,"org.springframework.boot:spring-boot-maven-plugin");
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
                .descriptorRef("artifact")
                .build();
    }
}
