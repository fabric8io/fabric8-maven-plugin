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
package io.fabric8.maven.core.model;

public class GroupArtifactVersion {

    private static final String PREFIX = "s";

    private String groupId;
    private String artifactId;
    private String version;

    public GroupArtifactVersion(String groupId, String artifactId, String version) {
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

    public boolean isSnapshot() {
        return getVersion() != null && getVersion().endsWith("SNAPSHOT");
    }

    /**
     * ArtifactId is used for setting a resource name (service, pod,...) in Kubernetes resource.
     * The problem is that a Kubernetes resource name must start by a char.
     * This method returns a valid string to be used as Kubernetes name.
     * @return Sanitized Kubernetes name.
     */
    public String getSanitizedArtifactId() {
        if (this.artifactId != null && !this.artifactId.isEmpty() && Character.isDigit(this.artifactId.charAt(0))) {
            return PREFIX + this.artifactId;
        }

        return this.artifactId;
    }
}
