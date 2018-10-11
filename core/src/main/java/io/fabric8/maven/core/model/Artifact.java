package io.fabric8.maven.core.model;

public class Artifact {

    private String groupId;
    private String artifactId;
    private String version;

    public Artifact() {
        this("unknown", "empty-project", "0");
    }

    public Artifact(String version) {
        this();
        this.version = version;
    }

    public Artifact(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

}
