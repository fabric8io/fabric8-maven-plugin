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
package io.fabric8.maven.core.util;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class AsciiDocParserTest {

    private static final String VALID_TABLE = "cols=2*,options=\"header\"]" + System.lineSeparator()
        + "|===" + System.lineSeparator()
        + "|Kind" + System.lineSeparator()
        + "|Filename Type" + System.lineSeparator()
        + System.lineSeparator()
        + "|ConfigMap" + System.lineSeparator()
        + "a|`cm`, `configmap`" + System.lineSeparator()
        + System.lineSeparator()
        + "|CronJob" + System.lineSeparator()
        + "a|`cronjob`" + System.lineSeparator()
        + "|===";

    private static final String NONE_END_VALID_TABLE = "cols=2*,options=\"header\"]" + System.lineSeparator()
        + "|===" + System.lineSeparator()
        + "|Kind" + System.lineSeparator()
        + "|Filename Type" + System.lineSeparator()
        + System.lineSeparator()
        + "|cm" + System.lineSeparator()
        + "a|ConfigMap" + System.lineSeparator()
        + System.lineSeparator()
        + "|cronjob" + System.lineSeparator()
        + "|CronJob" + System.lineSeparator();

    private static final String INVALID_TABLE_WITH_THREE_COLUMNS = "cols=2*,options=\"header\"]" + System.lineSeparator()
        + "|===" + System.lineSeparator()
        + "|Kind" + System.lineSeparator()
        + "|Filename Type" + System.lineSeparator()
        + System.lineSeparator()
        + "|cm" + System.lineSeparator()
        + "a|ConfigMap" + System.lineSeparator()
        + "a|ConfigMap" + System.lineSeparator()
        + System.lineSeparator()
        + "|cronjob" + System.lineSeparator()
        + "|CronJob" + System.lineSeparator()
        + "|===";

    @Test
    public void should_serialize_kind_and_filename_from_valid_asciidoc_table() {

        // Given

        final AsciiDocParser asciiDocParser = new AsciiDocParser();
        final ByteArrayInputStream tableContent = new ByteArrayInputStream(VALID_TABLE.getBytes());

        // When

        final Map<String, List<String>> serializedContent = asciiDocParser.serializeKindFilenameTable(tableContent);

        // Then
        final Map<String, List<String>> expectedSerlializedContent = new HashMap<>();
        expectedSerlializedContent.put("ConfigMap", Arrays.asList("cm", "configmap"));
        expectedSerlializedContent.put("CronJob", Arrays.asList("cronjob"));

        assertThat(serializedContent)
            .containsAllEntriesOf(expectedSerlializedContent);
    }

    @Test
    public void should_throw_exception_if_no_end_of_table() {

        // Given

        final AsciiDocParser asciiDocParser = new AsciiDocParser();
        final ByteArrayInputStream tableContent = new ByteArrayInputStream(NONE_END_VALID_TABLE.getBytes());

        // When

        Throwable error = catchThrowable(() -> asciiDocParser.serializeKindFilenameTable(tableContent));

        //Then

        assertThat(error).isInstanceOf(IllegalArgumentException.class);

    }

    @Test
    public void should_throw_exception_if_more_than_two_columns_are_present() {

        // Given

        final AsciiDocParser asciiDocParser = new AsciiDocParser();
        final ByteArrayInputStream tableContent = new ByteArrayInputStream(INVALID_TABLE_WITH_THREE_COLUMNS.getBytes());

        // When

        Throwable error = catchThrowable(() -> asciiDocParser.serializeKindFilenameTable(tableContent));

        //Then

        assertThat(error).isInstanceOf(IllegalArgumentException.class);

    }
}
