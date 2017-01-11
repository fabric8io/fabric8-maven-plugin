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

import java.lang.reflect.Method;
import java.util.*;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.utils.Lists;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @author roland
 * @since 14/11/16
 */
public class VolumePermissionEnricher extends BaseEnricher {

    public static final String VOLUME_STORAGE_CLASS_ANNOTATION = "volume.beta.kubernetes.io/storage-class";
    public static final String INIT_CONTAINER_ANNOTATION = "pod.alpha.kubernetes.io/init-containers";

    static Set<String> POD_TEMPLATE_SPEC_HOLDER =
        new HashSet<>(Arrays.asList("Deployment", "ReplicaSet", "ReplicationController", "DeploymentConfig"));

    private enum Config implements Configs.Key {
        permission {{ d = "777"; }};

        public String def() { return d; } protected String d;
    }

    public VolumePermissionEnricher(EnricherContext buildContext) {
        super(buildContext, "fmp-volume-permission");
    }

    @Override
    public void adapt(KubernetesListBuilder builder) {
        for (HasMetadata item : Lists.notNullList(builder.getItems())) {
            if (POD_TEMPLATE_SPEC_HOLDER.contains(item.getKind())) {
                addPersistentVolumeInitContainerChmod(item, getPodTemplateSpec(item));
            } else if ("PersistentVolumeClaim".equals(item.getKind())) {
                ensureVolumeStorageClass((PersistentVolumeClaim) item);
            }
        }
        super.adapt(builder);
    }

    private void ensureVolumeStorageClass(PersistentVolumeClaim pvc) {
        // lets ensure we have a default storage class so that PVs will get dynamically created OOTB
        Map<String, String> annotations = KubernetesHelper.getOrCreateAnnotations(pvc);
        if (!annotations.containsKey(VOLUME_STORAGE_CLASS_ANNOTATION)) {
            annotations.put(VOLUME_STORAGE_CLASS_ANNOTATION, "standard");
        }
    }


    private void addPersistentVolumeInitContainerChmod(HasMetadata entity, PodTemplateSpec template) {
        if (template == null) {
            return;
        }

        PodSpec podSpec = template.getSpec();
        if (podSpec == null) {
            return;
        }

        if (!checkForPvc(podSpec)) {
            return;
        }

        List<Container> containers = podSpec.getContainers();
        if (containers == null) {
            return;
        }

        String pvAnnotation = createPvAnnotation(podSpec);
        log.verbose("Adding annotation %s for changing persistent volumes access mode to %s",
                    INIT_CONTAINER_ANNOTATION, getConfig(Config.permission) );

        ObjectMeta metadata = ensureMetadata(template);
        Map<String, String> annotations = ensureAnnotations(metadata);

        annotations.put(INIT_CONTAINER_ANNOTATION, pvAnnotation);
    }

    private Map<String, String> ensureAnnotations(ObjectMeta metadata) {
        Map<String, String> annotations = metadata.getAnnotations();
        if (annotations == null) {
            annotations = new HashMap<String, String>();
            metadata.setAnnotations(annotations);
        } return annotations;
    }

    private ObjectMeta ensureMetadata(PodTemplateSpec template) {
        ObjectMeta metadata = template.getMetadata();
        if (metadata == null) {
            metadata = new ObjectMeta();
            template.setMetadata(metadata);
        }
        return metadata;
    }

    private String createPvAnnotation(PodSpec podSpec) {
        Map<String, String> mountPoints = extractMountPoints(podSpec);

        JSONArray anno = new JSONArray();
        JSONObject entry = new JSONObject();
        entry.put("name","init");
        entry.put("image","busybox");
        entry.put("imagePullPolicy","IfNotPresent");
        entry.put("command", createChmodCommandArray(mountPoints));
        entry.put("volumeMounts", createMounts(mountPoints));
        anno.put(entry);
        return anno.toString();
    }

    private JSONArray createChmodCommandArray(Map<String, String> mountPoints) {
        JSONArray ret = new JSONArray();
        ret.put("chmod");
        ret.put(getConfig(Config.permission));
        Set<String> uniqueNames = new HashSet<String>(mountPoints.values());
        for (String name : uniqueNames) {
            ret.put(name);
        }
        return ret;
    }

    private JSONArray createMounts(Map<String, String> mountPoints) {
        JSONArray ret = new JSONArray();
        for (Map.Entry<String,String> entry : mountPoints.entrySet()) {
            JSONObject mount = new JSONObject();
            mount.put("name", entry.getKey());
            mount.put("mountPath", entry.getValue());
            ret.put(mount);
        }
        return ret;
    }

    private Map<String, String> extractMountPoints(PodSpec podSpec) {
        Map<String, String> nameToMount = new TreeMap<>();

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

    private PodTemplateSpec getPodTemplateSpec(HasMetadata item) {
        try {
            Method specGet = item.getClass().getMethod("getSpec");
            Object spec = specGet.invoke(item);
            if (spec != null) {
                Method templateGet = spec.getClass().getMethod("getTemplate");
                return (PodTemplateSpec) templateGet.invoke(spec);
            } else {
                return null;
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Cannot extract pod template from " + item + ": " + e,e);
        }
    }
}
