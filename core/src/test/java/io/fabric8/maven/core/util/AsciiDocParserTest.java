package io.fabric8.maven.core.util;

import java.io.ByteArrayInputStream;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class AsciiDocParserTest {

    private static final String VALID_TABLE = "cols=2*,options=\"header\"]" + System.lineSeparator()
        + "|===" + System.lineSeparator()
        + "|Kind" + System.lineSeparator()
        + "|Filename" + System.lineSeparator()
        + System.lineSeparator()
        + "|cm" + System.lineSeparator()
        + "a|ConfigMap" + System.lineSeparator()
        + System.lineSeparator()
        + "|cronjob" + System.lineSeparator()
        + "|CronJob" + System.lineSeparator()
        + "|===";

    private static final String NONE_END_VALID_TABLE = "cols=2*,options=\"header\"]" + System.lineSeparator()
        + "|===" + System.lineSeparator()
        + "|Kind" + System.lineSeparator()
        + "|Filename" + System.lineSeparator()
        + System.lineSeparator()
        + "|cm" + System.lineSeparator()
        + "a|ConfigMap" + System.lineSeparator()
        + System.lineSeparator()
        + "|cronjob" + System.lineSeparator()
        + "|CronJob" + System.lineSeparator();

    private static final String INVALID_TABLE_WITH_THREE_COLUMNS = "cols=2*,options=\"header\"]" + System.lineSeparator()
        + "|===" + System.lineSeparator()
        + "|Kind" + System.lineSeparator()
        + "|Filename" + System.lineSeparator()
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

        final String[] serializedContent = asciiDocParser.serializeKindFilenameTable(tableContent);

        // Then

        assertThat(serializedContent).containsExactly("cm", "ConfigMap", "cronjob", "CronJob");
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
