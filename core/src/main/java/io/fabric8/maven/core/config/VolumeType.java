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

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;

public enum VolumeType {

    HOST_PATH("hostPath") {
        @Override
        public Volume fromConfig(VolumeConfig config) {
            return new VolumeBuilder()
                    .withName(config.getName())
                    .withNewHostPath(config.getPath())
                    .build();
        }
    }, EMPTY_DIR("emptyDir") {
        @Override
        public Volume fromConfig(VolumeConfig config) {
            return new VolumeBuilder()
                    .withName(config.getName())
                    .withNewEmptyDir().withMedium(config.getMedium()).endEmptyDir()
                    .build();

        }
    }, GIT_REPO("gitRepo") {
        public Volume fromConfig(VolumeConfig config) {
            String repository = config.getRepository();
            String revision = config.getRevision();
            return new VolumeBuilder()
                    .withName(config.getName())
                    .withNewGitRepo().withRepository(repository).withRevision(revision).endGitRepo()
                    .build();
        }
    }, SECRET("secret") {
        public Volume fromConfig(VolumeConfig config) {
            String secretName = config.getSecretName();
            return new VolumeBuilder()
                    .withName(config.getName())
                    .withNewSecret().withSecretName(secretName).endSecret()
                    .build();
        }
    }, NFS_PATH("nfsPath") {
        public Volume fromConfig(VolumeConfig config) {
            String path = config.getPath();
            String server = config.getServer();
            Boolean readOnly = config.getReadOnly();
            return new VolumeBuilder()
                    .withName(config.getName())
                    .withNewNfs(path, readOnly, server)
                    .build();
        }
    }, CGE_DISK("gcePdName") {
        public Volume fromConfig(VolumeConfig config) {

            String pdName = config.getPdName();
            String fsType = config.getFsType();
            Integer partition = config.getPartition();
            Boolean readOnly = config.getReadOnly();

            return new VolumeBuilder()
                    .withName(config.getName())
                    .withNewGcePersistentDisk(fsType, partition, pdName, readOnly)
                    .build();
        }

    }, GLUSTER_FS_PATH("glusterFsPath") {
        public Volume fromConfig(VolumeConfig config) {
            String path = config.getPath();
            String endpoints = config.getEndpoints();
            Boolean readOnly = config.getReadOnly();

            return new VolumeBuilder()
                    .withName(config.getName())
                    .withNewGlusterfs(path, endpoints, readOnly)
                    .build();
        }

    }, PERSISTENT_VOLUME_CLAIM("persistentVolumeClaim") {
        public Volume fromConfig(VolumeConfig config) {
            String claimRef = config.getClaimRef();
            Boolean readOnly = config.getReadOnly();

            return new VolumeBuilder()
                    .withName(config.getName())
                    .withNewPersistentVolumeClaim(claimRef, readOnly)
                    .build();
        }

    };

    private final String type;
    public abstract Volume fromConfig(VolumeConfig config);
    VolumeType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    private static final Map<String, VolumeType> VOLUME_TYPES = new HashMap<>();
    static {
        for (VolumeType volumeType : VolumeType.values()) {
            VOLUME_TYPES.put(volumeType.getType(), volumeType);
        }
    }

    public static VolumeType typeFor(String type) {
        return VOLUME_TYPES.get(type);
    }
}
