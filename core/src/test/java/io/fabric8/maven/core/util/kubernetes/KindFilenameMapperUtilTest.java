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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;

public class KindFilenameMapperUtilTest {

    private static final String MAPPING_PROPERTIES = "Var=foo, bar";
    private static final String OVERRIDE_MAPPING_PROPERTIES = "ConfigMap=foo, bar";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

    @Test
    public void should_load_default_mapping_file() {

        // Given

        // When

        final Map<String, List<String>> mappings = KindFilenameMapperUtil.loadMappings();

        // Then
        final Map<String, List<String>> expectedSerlializedContent = new HashMap<>();
        expectedSerlializedContent.put("ConfigMap", Arrays.asList("cm", "configmap"));
        expectedSerlializedContent.put("CronJob", Arrays.asList("cj", "cronjob"));
        expectedSerlializedContent.put("Pod", Arrays.asList("pd", "pod"));
        assertThat(mappings).containsAllEntriesOf(expectedSerlializedContent);

    }

    @Test
    public void should_load_mappings_from_custom_properties_file() throws IOException {

        // Given

        final File customMappings = temporaryFolder.newFile("custom.properties");
        Files.write(customMappings.toPath(), MAPPING_PROPERTIES.getBytes());
        System.setProperty("fabric8.mapping", customMappings.getAbsolutePath());

        // When

        final Map<String, List<String>> mappings = KindFilenameMapperUtil.loadMappings();

        // Then
        final Map<String, List<String>> expectedSerlializedContent = new HashMap<>();
        expectedSerlializedContent.put("ConfigMap", Arrays.asList("cm", "configmap"));
        expectedSerlializedContent.put("CronJob", Arrays.asList("cj", "cronjob"));
        expectedSerlializedContent.put("Var", Arrays.asList("foo", "bar"));
        assertThat(mappings).containsAllEntriesOf(expectedSerlializedContent);

    }

    @Test
    public void should_load_mappings_and_override_from_custom_properties_file() throws IOException {

        // Given

        final File customMappings = temporaryFolder.newFile("custom.properties");
        Files.write(customMappings.toPath(), OVERRIDE_MAPPING_PROPERTIES.getBytes());
        System.setProperty("fabric8.mapping", customMappings.getAbsolutePath());

        // When

        final Map<String, List<String>> mappings = KindFilenameMapperUtil.loadMappings();

        // Then
        final Map<String, List<String>> expectedSerlializedContent = new HashMap<>();
        expectedSerlializedContent.put("ConfigMap", Arrays.asList("foo", "bar"));
        expectedSerlializedContent.put("CronJob", Arrays.asList("cj", "cronjob"));
        assertThat(mappings).containsAllEntriesOf(expectedSerlializedContent);

    }

}
