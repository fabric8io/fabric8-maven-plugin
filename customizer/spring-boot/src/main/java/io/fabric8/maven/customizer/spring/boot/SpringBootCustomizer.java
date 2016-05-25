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

package io.fabric8.maven.customizer.spring.boot;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.fabric8.maven.customizer.api.BaseCustomizer;
import io.fabric8.maven.customizer.api.MavenCustomizerContext;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import org.apache.maven.project.MavenProject;

/**
 * @author roland
 * @since 15/05/16
 */
public class SpringBootCustomizer extends BaseCustomizer {

    public SpringBootCustomizer(MavenCustomizerContext context) {
        super(context);
    }

    @Override
    public List<ImageConfiguration> customize(List<ImageConfiguration> configs) {
        if (!containsBuildConfiguration(configs) && isApplicable()) {
            ImageConfiguration.Builder imageBuilder = new ImageConfiguration.Builder();
            BuildImageConfiguration.Builder buildBuilder = new BuildImageConfiguration.Builder()
                .assembly(createAssembly())
                .from(getBaseImage())
                .ports(extractPorts());
            addLatestIfSnapshot(buildBuilder);
            imageBuilder
                .name(extractImageName())
                .buildConfig(buildBuilder.build());
            configs.add(imageBuilder.build());
            return configs;
        } else {
            return configs;
        }
    }

    private void addLatestIfSnapshot(BuildImageConfiguration.Builder buildBuilder) {
        MavenProject project = getProject();
        if (project.getVersion().endsWith("-SNAPSHOT")) {
            buildBuilder.tags(Collections.singletonList("latest"));
        }
    }

    private String extractImageName() {
        MavenProject project = getProject();
        return prepareUserName(project.getGroupId()) + "/" +
               project.getArtifactId().toLowerCase() + ":" +
               project.getVersion();
    }

    private String prepareUserName(String groupId) {
        int idx = groupId.lastIndexOf(".");
        String last = groupId.substring(idx != -1 ? idx : 0);
        StringBuilder ret = new StringBuilder();
        for (char c : last.toCharArray()) {
            if (Character.isLetter(c) || Character.isDigit(c)) {
                ret.append(c);
            }
        }
        return ret.toString();
    }

    private List<String> extractPorts() {
        return Arrays.asList("8080");
    }

    private String getBaseImage() {
        return "fabric8/java-alpine-openjdk8-jdk";
    }

    private AssemblyConfiguration createAssembly() {
        return
            new AssemblyConfiguration.Builder()
                .basedir("/app")
                .descriptorRef("spring-boot")
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
        MavenProject project = getProject();
        return project.getPlugin("org.springframework.boot:spring-boot-maven-plugin") != null;
    }
}
