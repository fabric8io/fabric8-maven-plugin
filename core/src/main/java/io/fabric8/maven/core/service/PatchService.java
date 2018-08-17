/**
 *  Copyright 2005-2016 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package io.fabric8.maven.core.service;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpec;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerSpec;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.maven.core.util.kubernetes.OpenshiftHelper;
import io.fabric8.maven.core.util.kubernetes.UserConfigurationCompare;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigSpec;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.ImageStreamSpec;
import io.fabric8.openshift.client.OpenShiftClient;

import java.util.HashMap;
import java.util.Map;

import static io.fabric8.maven.core.util.kubernetes.KubernetesHelper.getKind;
import static io.fabric8.maven.core.util.kubernetes.KubernetesHelper.getName;

public class PatchService {
    private final KubernetesClient kubernetesClient;
    private final Logger log;

    public PatchService(KubernetesClient client, Logger log) {
        this.kubernetesClient = client;
        this.log = log;
    }

    public HasMetadata compareAndPatchEntity(String namespace, Object newDto, Object oldDto) {
        if (newDto instanceof Pod) {
            return patchPod(namespace, (Pod) newDto, (Pod) oldDto);
        } else if (newDto instanceof ReplicationController) {
            return patchReplicationController(namespace, (ReplicationController) newDto, (ReplicationController)oldDto);
        } else if (newDto instanceof Service) {
            return patchService(namespace, (Service) newDto, (Service) oldDto);
        } else if (newDto instanceof BuildConfig) {
            return patchBuildConfig(namespace, (BuildConfig) newDto, (BuildConfig) oldDto);
        } else if (newDto instanceof ImageStream) {
            return patchImageStream(namespace, (ImageStream) newDto, (ImageStream) oldDto);
        } else if (newDto instanceof Secret) {
            return patchSecret(namespace, (Secret) newDto, (Secret) oldDto);
        } else if (newDto instanceof PersistentVolumeClaim) {
            return patchPersistentVolumeClaim(namespace, (PersistentVolumeClaim) newDto, (PersistentVolumeClaim) oldDto);
        } else if (newDto instanceof HasMetadata) {
            HasMetadata entity = (HasMetadata) newDto;
            try {
                log.info("Patching " + getKind(entity) + " " + getName(entity));
                return kubernetesClient.resource(entity).inNamespace(namespace).createOrReplace();
            } catch (Exception e) {
                throw new RuntimeException("Failed to patch " + getKind(entity) + e, e);
            }
        } else {
            throw new IllegalArgumentException("Unknown entity type " + newDto);
        }
    }

    private HasMetadata patchPod(String namespace, Pod newObj, Pod oldObj) {
        KubernetesResource toUpdateMetadata = null, toUpdateSpec = null;

        if(!UserConfigurationCompare.configEqual(newObj.getMetadata(), oldObj.getMetadata())) {
            toUpdateMetadata = newObj.getMetadata();
        }
        if(!UserConfigurationCompare.configEqual(newObj.getSpec(), oldObj.getSpec())) {
            toUpdateSpec = newObj.getSpec();
        }
        if(toUpdateMetadata != null && toUpdateSpec != null) {
            return kubernetesClient.pods().inNamespace(namespace).withName(oldObj.getMetadata().getName()).edit()
                    .withMetadata((ObjectMeta)toUpdateMetadata)
                    .withSpec((PodSpec)toUpdateSpec)
                    .done();
        } else if(toUpdateMetadata != null) {
            return kubernetesClient.pods().inNamespace(namespace).withName(oldObj.getMetadata().getName()).edit()
                    .withMetadata((ObjectMeta)toUpdateMetadata)
                    .done();
        } else if(toUpdateSpec != null) {
            return kubernetesClient.pods().inNamespace(namespace).withName(oldObj.getMetadata().getName()).edit()
                    .withSpec((PodSpec)toUpdateSpec)
                    .done();
        } else {
            return oldObj;
        }
    }

    private HasMetadata patchReplicationController(String namespace, ReplicationController newObj, ReplicationController oldObj) {
        KubernetesResource toUpdateMetadata = null, toUpdateSpec = null;

        if(!UserConfigurationCompare.configEqual(newObj.getMetadata(), oldObj.getMetadata())) {
            toUpdateMetadata = newObj.getMetadata();
        }
        if(!UserConfigurationCompare.configEqual(newObj.getSpec(), oldObj.getSpec())) {
            toUpdateSpec = newObj.getSpec();
        }
        if(toUpdateMetadata != null && toUpdateSpec != null) {
            return kubernetesClient.replicationControllers().inNamespace(namespace).withName(oldObj.getMetadata().getName()).edit()
                    .withMetadata((ObjectMeta)toUpdateMetadata)
                    .withSpec((ReplicationControllerSpec)toUpdateSpec)
                    .done();
        } else if(toUpdateMetadata != null) {
            return kubernetesClient.replicationControllers().inNamespace(namespace).withName(oldObj.getMetadata().getName()).edit()
                    .withMetadata((ObjectMeta)toUpdateMetadata)
                    .done();
        } else if(toUpdateSpec != null) {
            return kubernetesClient.replicationControllers().inNamespace(namespace).withName(oldObj.getMetadata().getName()).edit()
                    .withSpec((ReplicationControllerSpec) toUpdateSpec)
                    .done();
        } else {
            return oldObj;
        }
    }

    private HasMetadata patchService(String namespace, Service newObj, Service oldObj) {
        KubernetesResource toUpdateMetadata = null, toUpdateSpec = null;

        if(!UserConfigurationCompare.configEqual(newObj.getMetadata(), oldObj.getMetadata())) {
            toUpdateMetadata = newObj.getMetadata();
        }
        if(!UserConfigurationCompare.configEqual(newObj.getSpec(), oldObj.getSpec())) {
            toUpdateSpec = newObj.getSpec();
        }
        if(toUpdateMetadata != null && toUpdateSpec != null) {
            return kubernetesClient.services().inNamespace(namespace).withName(oldObj.getMetadata().getName()).edit()
                    .withMetadata((ObjectMeta)toUpdateMetadata)
                    .withSpec((ServiceSpec)toUpdateSpec)
                    .done();
        } else if(toUpdateMetadata != null) {
            return kubernetesClient.services().inNamespace(namespace).withName(oldObj.getMetadata().getName()).edit()
                    .withMetadata((ObjectMeta)toUpdateMetadata)
                    .done();
        } else if(toUpdateSpec != null) {
            return kubernetesClient.services().inNamespace(namespace).withName(oldObj.getMetadata().getName()).edit()
                    .withSpec((ServiceSpec) toUpdateSpec)
                    .done();
        } else {
            return oldObj;
        }
    }

    private HasMetadata patchBuildConfig(String namespace, BuildConfig newObj, BuildConfig oldObj) {
        OpenShiftClient openShiftClient = OpenshiftHelper.asOpenShiftClient(kubernetesClient);
        KubernetesResource toUpdateMetadata = null, toUpdateSpec = null;

        if(!UserConfigurationCompare.configEqual(newObj.getMetadata(), oldObj.getMetadata())) {
            toUpdateMetadata = newObj.getMetadata();
        }
        if(!UserConfigurationCompare.configEqual(newObj.getSpec(), oldObj.getSpec())) {
            toUpdateSpec = newObj.getSpec();
        }
        if(toUpdateMetadata != null && toUpdateSpec != null) {
            return openShiftClient.buildConfigs().inNamespace(namespace).withName(oldObj.getMetadata().getName()).edit()
                    .withMetadata((ObjectMeta)toUpdateMetadata)
                    .withSpec((BuildConfigSpec)toUpdateSpec)
                    .done();
        } else if(toUpdateMetadata != null) {
            return openShiftClient.buildConfigs().inNamespace(namespace).withName(oldObj.getMetadata().getName()).edit()
                    .withMetadata((ObjectMeta)toUpdateMetadata)
                    .done();
        } else if(toUpdateSpec != null) {
            return openShiftClient.buildConfigs().inNamespace(namespace).withName(oldObj.getMetadata().getName()).edit()
                    .withSpec((BuildConfigSpec) toUpdateSpec)
                    .done();
        } else {
            return oldObj;
        }
    }

    private HasMetadata patchImageStream(String namespace, ImageStream newObj, ImageStream oldObj) {
        OpenShiftClient openShiftClient = OpenshiftHelper.asOpenShiftClient(kubernetesClient);
        KubernetesResource toUpdateMetadata = null, toUpdateSpec = null;

        if(!UserConfigurationCompare.configEqual(newObj.getMetadata(), oldObj.getMetadata())) {
            toUpdateMetadata = newObj.getMetadata();
        }
        if(!UserConfigurationCompare.configEqual(newObj.getSpec(), oldObj.getSpec())) {
            toUpdateSpec = newObj.getSpec();
        }
        if(toUpdateMetadata != null && toUpdateSpec != null) {
            return openShiftClient.imageStreams().inNamespace(namespace).withName(oldObj.getMetadata().getName()).edit()
                    .withMetadata((ObjectMeta)toUpdateMetadata)
                    .withSpec((ImageStreamSpec)toUpdateSpec)
                    .done();
        } else if(toUpdateMetadata != null) {
            return openShiftClient.imageStreams().inNamespace(namespace).withName(oldObj.getMetadata().getName()).edit()
                    .withMetadata((ObjectMeta)toUpdateMetadata)
                    .done();
        } else if(toUpdateSpec != null) {
            return openShiftClient.imageStreams().inNamespace(namespace).withName(oldObj.getMetadata().getName()).edit()
                    .withSpec((ImageStreamSpec) toUpdateSpec)
                    .done();
        } else {
            return oldObj;
        }
    }

    private HasMetadata patchSecret(String namespace, Secret newObj, Secret oldObj) {
        KubernetesResource toUpdateMetadata = null;
        Map<String, String> toUpdateSpec = new HashMap<>();

        if(!UserConfigurationCompare.configEqual(newObj.getMetadata(), oldObj.getMetadata())) {
            toUpdateMetadata = newObj.getMetadata();
        }
        if(!UserConfigurationCompare.configEqual(newObj.getData(), oldObj.getData())) {
            toUpdateSpec = newObj.getData();
        }
        if(toUpdateMetadata != null && toUpdateSpec != null) {
            return kubernetesClient.secrets().inNamespace(namespace).withName(oldObj.getMetadata().getName()).edit()
                    .withMetadata((ObjectMeta)toUpdateMetadata)
                    .withData(toUpdateSpec)
                    .done();
        } else if(toUpdateMetadata != null) {
            return kubernetesClient.secrets().inNamespace(namespace).withName(oldObj.getMetadata().getName()).edit()
                    .withMetadata((ObjectMeta)toUpdateMetadata)
                    .done();
        } else if(toUpdateSpec != null) {
            return kubernetesClient.secrets().inNamespace(namespace).withName(oldObj.getMetadata().getName()).edit()
                    .withData(toUpdateSpec)
                    .done();
        } else {
            return oldObj;
        }
    }

    private HasMetadata patchPersistentVolumeClaim(String namespace, PersistentVolumeClaim newObj, PersistentVolumeClaim oldObj) {
        KubernetesResource toUpdateMetadata = null, toUpdateSpec = null;

        if(!UserConfigurationCompare.configEqual(newObj.getMetadata(), oldObj.getMetadata())) {
            toUpdateMetadata = newObj.getMetadata();
        }
        if(!UserConfigurationCompare.configEqual(newObj.getSpec(), oldObj.getSpec())) {
            toUpdateSpec = newObj.getSpec();
        }
        if(toUpdateMetadata != null && toUpdateSpec != null) {
            return kubernetesClient.persistentVolumeClaims().inNamespace(namespace).withName(oldObj.getMetadata().getName()).edit()
                    .withMetadata((ObjectMeta)toUpdateMetadata)
                    .withSpec((PersistentVolumeClaimSpec)toUpdateSpec)
                    .done();
        } else if(toUpdateMetadata != null) {
            return kubernetesClient.persistentVolumeClaims().inNamespace(namespace).withName(oldObj.getMetadata().getName()).edit()
                    .withMetadata((ObjectMeta)toUpdateMetadata)
                    .done();
        } else if(toUpdateSpec != null) {
            return kubernetesClient.persistentVolumeClaims().inNamespace(namespace).withName(oldObj.getMetadata().getName()).edit()
                    .withSpec((PersistentVolumeClaimSpec)toUpdateSpec)
                    .done();
        } else {
            return oldObj;
        }
    }
}
