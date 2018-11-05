/**
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
package io.fabric8.maven.plugin.mojo.build;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.core.config.ConfigMapEntry;
import io.fabric8.maven.core.config.ResourceConfig;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceMojoTest {

    @Test
    public void should_add_config_map_content_from_configuration() {

        // Given
        io.fabric8.maven.core.config.ConfigMap mockedConfigMap = new io.fabric8.maven.core.config.ConfigMap();

        final ConfigMapEntry standardConfigMapEntry = new ConfigMapEntry();
        standardConfigMapEntry.setName("A");
        standardConfigMapEntry.setValue("B");
        mockedConfigMap.addEntry(standardConfigMapEntry);

        final ConfigMapEntry fileConfigMapEntry = new ConfigMapEntry();
        fileConfigMapEntry.setFile("src/test/resources/test-application.properties");
        mockedConfigMap.addEntry(fileConfigMapEntry);

        final ResourceConfig.Builder resourceConfigBuilder = new ResourceConfig.Builder();
        resourceConfigBuilder.withConfigMap(mockedConfigMap);

        final KubernetesListBuilder builder = new KubernetesListBuilder();
        ResourceMojo resourceMojo = new ResourceMojo();

        // When
        resourceMojo.addConfigMapFromConfigurations(builder, mockedConfigMap);

        // Then
        final ConfigMap configMap = (ConfigMap) builder.buildFirstItem();

        final Map<String, String> data = configMap.getData();

        assertThat(data)
            .containsKey("test-application.properties")
            .containsKey("A");

    }

}
