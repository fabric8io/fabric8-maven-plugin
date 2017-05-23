package io.fabric8.maven.core.util.kubernetes;

/**
 * @author roland
 * @since 23.05.17
 */
public enum Fabric8Annotations {

    SERVICE_EXPOSE_URL("exposeUrl"),

    BUILD_ID("build-id"),
    BUILD_URL("build-url"),

    GIT_COMMIT("git-commit"),
    GIT_URL("git-url"),
    GIT_BRANCH("git-branch"),
    GIT_CLONE_URL("git-clone-url"),
    GIT_LOCAL_CLONE_URL("local-git-url"),

    DOCS_URL("docs-url"),

    METRICS_PATH("metrics-path"),

    ICON_URL("iconUrl"),

    ISSUE_SYSTEM("issue-system"),
    ISSUE_TRACKER_URL("issue-tracker-url"),

    SCM_CONNECTION("scm-con-url"),
    SCM_DEVELOPER_CONNECTION("scm-devcon-url"),
    SCM_TAG("scm-tag"),
    SCM_URL("scm-url"),

    TARGET_PLATFORM("target-platform");

    private final String annotation;

    Fabric8Annotations(String anno) {
        this.annotation = "fabric8.io/" + anno;
    }

    public String value() {
        return annotation;
    }

    @Override
    public String toString() {
        return value();
    }
}
