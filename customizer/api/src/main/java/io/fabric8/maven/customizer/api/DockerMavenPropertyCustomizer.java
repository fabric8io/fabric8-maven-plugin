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
package io.fabric8.maven.customizer.api;

import io.fabric8.maven.core.util.MavenProperties;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.utils.Strings;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * If an image name has any docker maven properties included from {@link MavenProperties}, lets replace them
 */
public class DockerMavenPropertyCustomizer extends BaseCustomizer {
    public DockerMavenPropertyCustomizer(MavenCustomizerContext context) {
        super(context, "docker.maven.properties");
    }

    @Override
    public List<ImageConfiguration> customize(List<ImageConfiguration> existingConfigs) {
        List<ImageConfiguration> answer = new ArrayList<>();
        for (ImageConfiguration config : existingConfigs) {
            String name = config.getName();
            String newName = convertName(name);
            if (Objects.equals(name, newName)) {
                answer.add(config);
            } else {
                // TODO would be nice to be able to instantiate a builder from a copy - in case we miss stuff!
                ImageConfiguration.Builder builder = new ImageConfiguration.Builder();
                builder.alias(config.getAlias());
                builder.buildConfig(config.getBuildConfiguration());
                builder.externalConfig(config.getExternalConfig());
                builder.name(newName);
                builder.runConfig(config.getRunConfiguration());
                builder.watchConfig(config.getWatchConfiguration());
                answer.add(builder.build());
            }
        }
        return answer;
    }

    protected String convertName(String name) {
        String answer = name;
        Properties properties = getProject().getProperties();
        for (String propertyName : MavenProperties.MAVEN_PROPERTIES) {
            String value = properties.getProperty(propertyName);
            if (Strings.isNotBlank(value)) {
                answer = Strings.replaceAllWithoutRegex(answer, "${" + propertyName + "}", value);
            }
        }
        return answer;
    }
}
