package io.fabric8.maven.core.util.validator;

/**
 * Model to represent ignore validation rules as tuple of json tree path and constraint type
 * for resource descriptor validations
 */
public class IgnoreRule {

    public static final String REQUIRED = "required";

    private final String path;
    private final String type;

    public IgnoreRule(String path, String type) {
        this.path = path;
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public String getType() {
        return type;
    }
}
