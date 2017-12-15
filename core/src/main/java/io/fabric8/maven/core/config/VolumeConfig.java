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

import java.util.List;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Configuration for a single volume
 *
 * @author roland
 * @since 22/03/16
 */
public class VolumeConfig {

    @Parameter
    private String type;

    @Parameter
    private String name;

    // List of mount paths of this volume
    @Parameter
    private List<String> mounts;

    @Parameter
    private String path;

    @Parameter
    private String medium;

    @Parameter
    private String repository;

    @Parameter
    private String revision;

    @Parameter
    private String secretName;

    @Parameter
    private String server;

    @Parameter
    private Boolean readOnly;

    @Parameter
    private String pdName;

    @Parameter
    private String fsType;

    @Parameter
    private Integer partition;

    @Parameter
    private String endpoints;

    @Parameter
    private String claimRef;

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getMedium() {
        return medium;
    }

    public String getRepository() {
        return repository;
    }

    public String getRevision() {
        return revision;
    }

    public String getSecretName() {
        return secretName;
    }

    public String getServer() {
        return server;
    }

    public Boolean getReadOnly() {
        return readOnly;
    }

    public String getPdName() {
        return pdName;
    }

    public String getFsType() {
        return fsType;
    }

    public Integer getPartition() {
        return partition;
    }

    public String getEndpoints() {
        return endpoints;
    }

    public String getClaimRef() {
        return claimRef;
    }

    public List<String> getMounts() {
        return mounts;
    }

    public static class Builder {
        private VolumeConfig volumeConfig = new VolumeConfig();

        public VolumeConfig.Builder name(String name) {
            volumeConfig.name = name;
            return this;
        }

        public VolumeConfig.Builder mounts(List<String> mounts) {
            volumeConfig.mounts = mounts;
            return this;
        }

        public VolumeConfig.Builder type(String type) {
            volumeConfig.type = type;
            return this;
        }

        public VolumeConfig.Builder path(String path) {
            volumeConfig.path = path;
            return this;
        }

        public VolumeConfig build() {
            return volumeConfig;
        }
    }

    // TODO: Change to rich configuration as described in http://blog.sonatype.com/2011/03/configuring-plugin-goals-in-maven-3/

}
