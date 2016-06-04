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
import java.util.Properties;

import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.customizer.api.BaseCustomizer;
import io.fabric8.maven.customizer.api.CustomizerConfiguration;
import io.fabric8.maven.customizer.api.MavenCustomizerContext;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import org.apache.maven.project.MavenProject;

import static io.fabric8.maven.core.util.MavenProperties.DOCKER_IMAGE_NAME;
import static io.fabric8.maven.core.util.MavenProperties.DOCKER_IMAGE_USER;

/**
 * @author roland
 * @since 15/05/16
 */
public class SpringBootCustomizer extends BaseCustomizer {

    public SpringBootCustomizer(MavenCustomizerContext context) {
        super(context, "spring.boot");
    }

    private enum Config implements Configs.Key {
        combine {{ super.d = "false"; }};

        public String def() { return d; } private String d;
    }

    @Override
    public List<ImageConfiguration> customize(List<ImageConfiguration> configs) {
        MavenProject project = getProject();
        Properties properties = project.getProperties();
        if (!properties.containsKey(DOCKER_IMAGE_USER)) {
            properties.put(DOCKER_IMAGE_USER, prepareUserName());
        }
        if (!properties.containsKey(DOCKER_IMAGE_NAME)) {
            properties.put(DOCKER_IMAGE_NAME, prepareName());
        }
        boolean includeDefaultImage = false;
        if (isApplicable()) {
            boolean combineEnabled = Configs.asBoolean(getConfig(Config.combine));
            includeDefaultImage = !containsBuildConfiguration(configs) || combineEnabled;
        }
        if (includeDefaultImage && isApplicable()) {
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
        return prepareUserName() + "/" + prepareName() + ":" + prepareVersion();
    }

    private String prepareName() {
        MavenProject project = getProject();
        return project.getArtifactId().toLowerCase();
    }

    private String prepareUserName() {
        String groupId = getProject().getGroupId();
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

    private String prepareVersion() {
        MavenProject project = getProject();
        //return project.getProperties().getProperty(DOCKER_IMAGE_NAME, project.getVersion());
        return project.getVersion();
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
