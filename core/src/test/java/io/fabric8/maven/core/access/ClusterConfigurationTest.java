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
package io.fabric8.maven.core.access;

import java.util.Properties;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClusterConfigurationTest {

    @Test
    public void should_lod_coniguration_from_properties() {

        // Given
        final ClusterConfiguration.Builder clusterConfigurationBuilder = new ClusterConfiguration.Builder();
        final Properties properties = new Properties();
        properties.put("fabric8.username", "aaa");
        properties.put("fabric8.password", "bbb");

        // When
        final ClusterConfiguration clusterConfiguration = clusterConfigurationBuilder.from(properties).build();

        // Then
        assertThat(clusterConfiguration.getConfig().getUsername()).isEqualTo("aaa");
        assertThat(clusterConfiguration.getConfig().getPassword()).isEqualTo("bbb");
    }

}
