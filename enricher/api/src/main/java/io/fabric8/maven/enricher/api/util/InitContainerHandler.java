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

package io.fabric8.maven.enricher.api.util;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.maven.core.util.JSONUtil;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.utils.Strings;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @author roland
 * @since 07/02/17
 */
public class InitContainerHandler {

    public static final String INIT_CONTAINER_ANNOTATION = "pod.alpha.kubernetes.io/init-containers";

    Logger log;

    public InitContainerHandler(Logger log) {
        this.log = log;
    }

    public boolean hasInitContainer(PodTemplateSpecBuilder builder, String name) {
        return getInitContainer(builder, name) != null;
    }

    public JSONObject getInitContainer(PodTemplateSpecBuilder builder, String name) {
        if (builder.hasMetadata()) {
            String initContainerAnnotation = builder.buildMetadata().getAnnotations().get(INIT_CONTAINER_ANNOTATION);
            if (Strings.isNotBlank(initContainerAnnotation)) {
                JSONArray initContainers = new JSONArray(initContainerAnnotation);
                for (int i = 0; i < initContainers.length(); i++) {
                    JSONObject obj = initContainers.getJSONObject(i);
                    String existingName = obj.getString("name");
                    if (name.equals(existingName)) {
                        return obj;
                    }
                }
            }
        }
        return null;
    }

    public void removeInitContainer(PodTemplateSpecBuilder builder, String initContainerName) {
        if (hasInitContainer(builder, initContainerName)) {
            ObjectMeta meta = builder.buildMetadata();
            Map<String, String> annos = meta.getAnnotations();
            JSONArray newInitContainers = removeFromInitContainersJson(annos.get(INIT_CONTAINER_ANNOTATION), initContainerName);
            if (newInitContainers.length() > 0) {
                annos.put(INIT_CONTAINER_ANNOTATION, newInitContainers.toString());
            } else {
                annos.remove(INIT_CONTAINER_ANNOTATION);
            }
            meta.setAnnotations(annos);
        }
    }

    private JSONArray removeFromInitContainersJson(String initContainersJson, String initContainerName) {
        JSONArray newInitContainers = new JSONArray();
        JSONArray initContainers = new JSONArray(initContainersJson);

        for (int i = 0; i < initContainers.length(); i++) {
            JSONObject obj = initContainers.getJSONObject(i);
            String existingName = obj.getString("name");
            if (!initContainerName.equals(existingName)) {
                newInitContainers.put(obj);
            }
        }
        return newInitContainers;
    }

    public void appendInitContainer(PodTemplateSpecBuilder builder, JSONObject initContainer) {
        String name = initContainer.getString("name");
        JSONObject existing = getInitContainer(builder, name);
        if (existing != null) {
            if (JSONUtil.equals(existing, initContainer)) {
                log.warn("Trying to add init-container %s a second time. Ignoring ....", name);
                return;
            } else {
                throw new IllegalArgumentException(
                    String.format("PodSpec %s already contains a different init container with name %s but can not add a second one with the same name. " +
                                  "Please choose a different name for the init container",
                                  builder.build().getMetadata().getName(), name));
            }
        }
        ensureMetadata(builder);
        String initContainerAnnotation = builder.buildMetadata().getAnnotations().get(INIT_CONTAINER_ANNOTATION);
        JSONArray initContainers = Strings.isNullOrBlank(initContainerAnnotation) ? new JSONArray() : new JSONArray(initContainerAnnotation);
        initContainers.put(initContainer);
        builder.editMetadata().addToAnnotations(INIT_CONTAINER_ANNOTATION, initContainers.toString()).endMetadata();
    }

    private void ensureMetadata(PodTemplateSpecBuilder obj) {
        if (obj.buildMetadata() == null) {
            obj.withNewMetadata().endMetadata();
        }
    }
}
