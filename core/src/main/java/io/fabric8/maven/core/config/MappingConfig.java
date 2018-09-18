package io.fabric8.maven.core.config;

import org.apache.maven.plugins.annotations.Parameter;

public class MappingConfig {

    @Parameter(required = true)
    private String kind;

    @Parameter(required = true)
    private String filenames;

    public String getKind() {
        return kind;
    }

    public String getFilenames() {
        return filenames;
    }

    public String[] getFilenamesAsArray() {
        return filenames.split(",\\s*");
    }
}
