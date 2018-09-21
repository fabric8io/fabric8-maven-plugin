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
package io.fabric8.maven.core.config;
/**
 * OpenShift build mode. Only used when mode is "openshift"
 *
 * @author roland
 * @since 01/08/16
 */
public enum OpenShiftBuildStrategy {

    // Constants used to extract extra information from a `fromExt` build configuration
    /**
     * S2i build with a binary source
     */
    s2i("S2I"),

    /**
     * Docker build with a binary source
     */
    docker("Docker");

    // Source strategy elemens
    public enum SourceStrategy {
        kind,
        namespace,
        name;

        public String key() {
            // Return the name, could be mapped if needed.
            return name();
        }
    }


    private final String label;

    private OpenShiftBuildStrategy(String label) {
        this.label = label;
    }

    /**
     * Check if the given type is same as the type stored in OpenShift
     *
     * @param type to check
     * @return
     */
    public boolean isSame(String type) {
        return type != null &&
               (type.equalsIgnoreCase("source") && this == s2i) ||
               (type.equalsIgnoreCase("docker") && this == docker);
    }

    public String getLabel() {
        return label;
    }
}
