/*
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
 * Mode how to create resouce descriptors
 *
 * @author roland
 * @since 25/05/16
 */
public enum PlatformMode {
    /**
     * Create resources descriptors for vanilla Kubernetes
     */
    kubernetes("kubernetes"),

    /**
     * Use special OpenShift features like BuildConfigs
     */
    openshift("openshift"),

    /**
     * Operate offline, e.g. create only the docker.tar but do not build. Otherwise like
     * plain kubernetes (not sure whether this is needed or maybe it should be a parallel config)
     */
    offline("kubernetes");

    private final String fileName;

    PlatformMode(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }
}
