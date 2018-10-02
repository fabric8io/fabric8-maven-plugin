package io.fabric8.maven.core.config;

public class MappingConfig {

    private String kind;

    private String filenameTypes;

    public String getKind() {
        return kind;
    }

    public String getFilenameTypes() {
        return filenameTypes;
    }

    public String[] getFilenamesAsArray() {
        if (this.filenameTypes == null) {
            return new String[0];
        }
        return filenameTypes.split(",\\s*");
    }

    public boolean isValid() {
        return kind != null &&  filenameTypes != null && filenameTypes.length() > 0;
    }

}
