package io.fabric8.maven.enricher.api;

import java.io.File;

public class Dependency {

    private String type;
    private String scope;
    private File location;

    public Dependency(String type, String scope, File location) {
        this.type = type;
        this.scope = scope;
        this.location = location;
    }

    public String getType() {
        return type;
    }

    public String getScope() {
        return scope;
    }

    public File getLocation() {
        return location;
    }
}
