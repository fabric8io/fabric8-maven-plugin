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
package io.fabric8.maven.core.util.kubernetes;

import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KubernetesResourceUtilTest {

    @Test
    public void should_load_kid_filename_mapper_in_bimap_fashion() {

        // Given

        // When

        final Map<String, String> filenameToKindMapper = KubernetesResourceUtil.FILENAME_TO_KIND_MAPPER;
        final Map<String, String> kindToFilenameMapper = KubernetesResourceUtil.KIND_TO_FILENAME_MAPPER;

        // Then
        assertThat(filenameToKindMapper)
            .containsEntry("cm", "ConfigMap")
            .containsEntry("configmap", "ConfigMap")
            .containsEntry("secret", "Secret");

        assertThat(kindToFilenameMapper)
            .containsEntry("ConfigMap", "configmap")
            .containsEntry("Secret", "secret");

    }

}
