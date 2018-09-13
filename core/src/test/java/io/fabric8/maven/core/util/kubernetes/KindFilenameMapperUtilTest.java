package io.fabric8.maven.core.util.kubernetes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;

public class KindFilenameMapperUtilTest {

    private static final String VALID_TABLE = "cols=2*,options=\"header\"]" + System.lineSeparator()
        + "|===" + System.lineSeparator()
        + "|Kind" + System.lineSeparator()
        + "|Filename" + System.lineSeparator()
        + System.lineSeparator()
        + "|xx" + System.lineSeparator()
        + "|yy" + System.lineSeparator()
        + "|===";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

    @Test
    public void test() {
        System.setProperty("MY_PROPERTY", "myNewValue");
        // Test
    }


    @Test
    public void should_load_default_mapping_file() {

        // Given

        // When

        final String[] mappings = KindFilenameMapperUtil.loadMappings();

        // Then

        assertThat(mappings).contains("cm", "ConfigMap", "cronjob", "CronJob", "template", "Template");

    }

    @Test
    public void should_load_file_from_disk() throws IOException {

        // Given

        final File mapper = folder.newFile("test.adoc");
        Files.write(mapper.toPath(), VALID_TABLE.getBytes());
        System.setProperty("fabric8.mapping", mapper.getAbsolutePath());

        // When

        final String[] mappings = KindFilenameMapperUtil.loadMappings();

        // Then

        assertThat(mappings).contains("xx", "yy");

    }

}
