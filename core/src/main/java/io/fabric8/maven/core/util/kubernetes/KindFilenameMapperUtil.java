package io.fabric8.maven.core.util.kubernetes;

import io.fabric8.maven.core.util.AsciiDocParser;
import io.fabric8.maven.core.util.EnvUtil;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class KindFilenameMapperUtil {

    public static String[] loadMappings() {

        final String location =
            EnvUtil.getEnvVarOrSystemProperty("fabric8.mapping", "/fabric8/default-kind-filename-mapping.adoc");

        try (final InputStream mappingFile = loadContent(location)) {
            final AsciiDocParser asciiDocParser = new AsciiDocParser();
            return asciiDocParser.serializeKindFilenameTable(mappingFile);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static InputStream loadContent(String location) {
        InputStream resourceAsStream = KindFilenameMapperUtil.class.getResourceAsStream(location);

        if (resourceAsStream == null) {
            try {
                resourceAsStream = new FileInputStream(location);
            } catch (FileNotFoundException e) {
                throw new IllegalArgumentException(e);
            }
        }

        return resourceAsStream;
    }

}
