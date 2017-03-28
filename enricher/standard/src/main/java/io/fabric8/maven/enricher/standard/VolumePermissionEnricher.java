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

package io.fabric8.maven.enricher.standard;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSource;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.util.InitContainerHandler;
import io.fabric8.utils.Strings;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author roland
 * @since 14/11/16
 */
public class VolumePermissionEnricher extends BaseEnricher {

    public static final String ENRICHER_NAME = "fmp-volume-permission";
    static final String VOLUME_STORAGE_CLASS_ANNOTATION = "volume.beta.kubernetes.io/storage-class";

    private final InitContainerHandler initContainerHandler;

    enum Config implements Configs.Key {
        permission {{ d = "777"; }},
        defaultStorageClass {{ d = ""; }};

        public String def() { return d; } protected String d;
    }

    public VolumePermissionEnricher(EnricherContext buildContext) {
        super(buildContext, ENRICHER_NAME);
        initContainerHandler = new InitContainerHandler(buildContext.getLog());
    }

    @Override
    public void adapt(KubernetesListBuilder builder) {

        builder.accept(new TypedVisitor<PodTemplateSpecBuilder>() {
            @Override
            public void visit(PodTemplateSpecBuilder builder) {
                PodSpec podSpec = builder.buildSpec();
                if (podSpec == null) {
                    return;
                }

                if (!checkForPvc(podSpec)) {
                    return;
                }

                List<Container> containers = podSpec.getContainers();
                if (containers == null || containers.isEmpty()) {
                    return;
                }

                log.verbose("Adding init container for changing persistent volumes access mode to %s",
                        getConfig(Config.permission));
                if (!initContainerHandler.hasInitContainer(builder, ENRICHER_NAME)) {
                    initContainerHandler.appendInitContainer(builder, createPvInitContainer(podSpec));
                }
            }

            private boolean checkForPvc(PodSpec podSpec) {
                List<Volume> volumes = podSpec.getVolumes();
                if (volumes != null) {
                    for (Volume volume : volumes) {
                        PersistentVolumeClaimVolumeSource persistentVolumeClaim = volume.getPersistentVolumeClaim();
                        if (persistentVolumeClaim != null) {
                            return true;
                        }
                    }
                }
                return false;
            }

            private JSONObject createPvInitContainer(PodSpec podSpec) {
                Map<String, String> mountPoints = extractMountPoints(podSpec);

                JSONObject entry = new JSONObject();
                entry.put("name", ENRICHER_NAME);
                entry.put("image","busybox");
                entry.put("imagePullPolicy","IfNotPresent");
                entry.put("command", createChmodCommandArray(mountPoints));
                entry.put("volumeMounts", createMounts(mountPoints));
                return entry;
            }

            private JSONArray createChmodCommandArray(Map<String, String> mountPoints) {
                JSONArray ret = new JSONArray();
                ret.put("chmod");
                ret.put(getConfig(Config.permission));
                Set<String> uniqueNames = new LinkedHashSet<>(mountPoints.values());
                for (String name : uniqueNames) {
                    ret.put(name);
                }
                return ret;
            }

            private JSONArray createMounts(Map<String, String> mountPoints) {
                JSONArray ret = new JSONArray();
                for (Map.Entry<String, String> entry : mountPoints.entrySet()) {
                    JSONObject mount = new JSONObject();
                    mount.put("name", entry.getKey());
                    mount.put("mountPath", entry.getValue());
                    ret.put(mount);
                }
                return ret;
            }

            private Map<String, String> extractMountPoints(PodSpec podSpec) {
                Map<String, String> nameToMount = new LinkedHashMap<>();

                List<Volume> volumes = podSpec.getVolumes();
                if (volumes != null) {
                    for (Volume volume : volumes) {
                        PersistentVolumeClaimVolumeSource persistentVolumeClaim = volume.getPersistentVolumeClaim();
                        if (persistentVolumeClaim != null) {
                            String name = volume.getName();
                            String mountPath = getMountPath(podSpec.getContainers(), name);

                            nameToMount.put(name, mountPath);
                        }
                    }
                }
                return nameToMount;
            }

            private String getMountPath(List<Container> containers, String name){
                for (Container container : containers) {
                    List<VolumeMount> volumeMounts = container.getVolumeMounts();
                    if (volumeMounts != null) {
                        for (VolumeMount volumeMount : volumeMounts) {
                            if (name.equals(volumeMount.getName())){
                                return volumeMount.getMountPath();
                            }
                        }
                    }
                }
                throw new IllegalArgumentException("No matching volume mount found for volume "+ name);
            }

        });

        builder.accept(new TypedVisitor<PersistentVolumeClaimBuilder>() {
            @Override
            public void visit(PersistentVolumeClaimBuilder pvcBuilder) {
                // lets ensure we have a default storage class so that PVs will get dynamically created OOTB
                if (pvcBuilder.buildMetadata() == null) {
                    pvcBuilder.withNewMetadata().endMetadata();
                }
                String storageClass = getConfig(Config.defaultStorageClass);
                if (Strings.isNotBlank(storageClass) && !pvcBuilder.buildMetadata().getAnnotations().containsKey(VOLUME_STORAGE_CLASS_ANNOTATION)) {
                    pvcBuilder.editMetadata().addToAnnotations(VOLUME_STORAGE_CLASS_ANNOTATION, storageClass).endMetadata();
                }
            }
        });
    }
}
