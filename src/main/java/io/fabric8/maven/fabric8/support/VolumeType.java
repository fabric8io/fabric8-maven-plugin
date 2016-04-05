/*
 * Copyright 2005-2015 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.fabric8.maven.fabric8.support;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.maven.fabric8.config.VolumeConfiguration;

public enum VolumeType {

    HOST_PATH("hostPath") {
        @Override
        public Volume fromConfig(VolumeConfiguration config) {
            return new VolumeBuilder()
                    .withName(config.getName())
                    .withNewHostPath(config.getPath())
                    .build();
        }
    }, EMPTY_DIR("emptyDir") {
        @Override
        public Volume fromConfig(VolumeConfiguration config) {
            return new VolumeBuilder()
                    .withName(config.getName())
                    .withNewEmptyDir(config.getMedium())
                    .build();
        }
    }, GIT_REPO("gitRepo") {
        public Volume fromConfig(VolumeConfiguration config) {
            String repository = config.getRepository();
            String revision = config.getRevision();
            return new VolumeBuilder()
                    .withName(config.getName())
                    .withNewGitRepo().withRepository(repository).withRevision(revision).endGitRepo()
                    .build();
        }
    }, SECRET("secret") {
        public Volume fromConfig(VolumeConfiguration config) {
            String secretName = config.getSecretName();
            return new VolumeBuilder()
                    .withName(config.getName())
                    .withNewSecret(secretName)
                    .build();
        }
    }, NFS_PATH("nfsPath") {
        public Volume fromConfig(VolumeConfiguration config) {
            String path = config.getPath();
            String server = config.getServer();
            Boolean readOnly = config.getReadOnly();
            return new VolumeBuilder()
                    .withName(config.getName())
                    .withNewNfs(path, readOnly, server)
                    .build();
        }
    }, CGE_DISK("gcePdName") {
        public Volume fromConfig(VolumeConfiguration config) {

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
        public Volume fromConfig(VolumeConfiguration config) {
            String path = config.getPath();
            String endpoints = config.getEndpoints();
            Boolean readOnly = config.getReadOnly();

            return new VolumeBuilder()
                    .withName(config.getName())
                    .withNewGlusterfs(path, endpoints, readOnly)
                    .build();
        }

    }, PERSISTENT_VOLUME_CLAIM("persistentVolumeClaim") {
        public Volume fromConfig(VolumeConfiguration config) {
            String claimRef = config.getClaimRef();
            Boolean readOnly = config.getReadOnly();

            return new VolumeBuilder()
                    .withName(config.getName())
                    .withNewPersistentVolumeClaim(claimRef, readOnly)
                    .build();
        }

    };

    private final String type;

    public abstract Volume fromConfig(VolumeConfiguration config);

    VolumeType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }


    private static final Map<String, VolumeType> VOLUME_TYPES = new HashMap<>();

    private static final String VOLUME_PREFIX = "fabric8.volume";
    private static final String VOLUME_NAME_PREFIX = VOLUME_PREFIX + ".%s";
    public static final String VOLUME_PROPERTY = VOLUME_NAME_PREFIX + ".%s";

    private static final String VOLUME_GIT_REV = "revision";
    private static final String VOLUME_SECRET_NAME = "secret";

    private static final String VOLUME_NFS_SERVER = "nfsServer";
    private static final String VOLUME_GCE_FS_TYPE = "gceFsType";
    private static final String VOLUME_GLUSTERFS_ENDPOINTS = "endpoints";
    public static final String VOLUME_PVC_REQUEST_STORAGE = "requestStorage";

    private static final String READONLY = "readOnly";


    static {
        for (VolumeType volumeType : VolumeType.values()) {
            VOLUME_TYPES.put(volumeType.getType(), volumeType);
        }
    }

    public static final VolumeType typeFor(String type) {
        return VOLUME_TYPES.get(type);
    }

    private static Boolean toBool(Object obj) {
        if (obj == null) {
            return false;
        } else if (obj instanceof Boolean) {
            return (Boolean) obj;
        } else if (obj instanceof String) {
            return Boolean.parseBoolean((String) obj);
        } else {
            return false;
        }
    }

    private static Integer toInt(Object obj) {
        if (obj == null) {
            return 0;
        } else if (obj instanceof Integer) {
            return (Integer) obj;
        } else if (obj instanceof String) {
            return Integer.parseInt((String) obj);
        } else {
            return 0;
        }
    }

}
