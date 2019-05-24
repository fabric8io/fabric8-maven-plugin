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

    ISSUE_SYSTEM("issue-system"),
    ISSUE_TRACKER_URL("issue-tracker-url"),

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
