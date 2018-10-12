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

import java.io.File;

public class Dependency {

    // GAV coordinates of dependency
    private GroupArtifactVersion gav;

    // Dependency type ("jar", "war", ...)
    private String type;

    // Scope of the dependency ("compile", "runtime", ...)
    private String scope;

    // Location where the dependent jar is located
    private File location;

    public Dependency(GroupArtifactVersion gav, String type, String scope, File location) {
        this.gav = gav;
        this.type = type;
        this.scope = scope;
        this.location = location;
    }

    public GroupArtifactVersion getGav() {
        return gav;
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
