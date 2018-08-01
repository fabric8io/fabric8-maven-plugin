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
package io.fabric8.maven.core.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.maven.core.util.kubernetes.KubernetesHelper;
import io.fabric8.maven.core.util.kubernetes.KubernetesResourceUtil;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.openshift.api.model.Parameter;
import io.fabric8.openshift.api.model.Template;
import org.apache.commons.lang3.StringUtils;

import static io.fabric8.maven.core.util.kubernetes.KubernetesResourceUtil.getSourceUrlAnnotation;
import static io.fabric8.maven.core.util.kubernetes.KubernetesResourceUtil.setSourceUrlAnnotationIfNotSet;

/**
 */
public class OpenShiftDependencyResources {
    private final Map<KindAndName, HasMetadata> openshiftDependencyResources = new HashMap<>();
    private final Map<String, Parameter> templateParameters = new HashMap<>();
    private final Logger log;

    public OpenShiftDependencyResources(Logger log) {
        this.log = log;
    }

    public void addOpenShiftResources(List<HasMetadata> items, boolean isAppCatalog) {
        for (HasMetadata item : items) {
            if (item instanceof Template) {
                Template template = (Template) item;
                if (!KubernetesResourceUtil.isAppCatalogResource(template) && !isAppCatalog) {
                    List<HasMetadata> objects = notNullList(template.getObjects());
                    String sourceUrl = getSourceUrlAnnotation(template);
                    if (StringUtils.isNotBlank(sourceUrl)) {
                        for (HasMetadata object : objects) {
                            setSourceUrlAnnotationIfNotSet(object, sourceUrl);
                        }
                    }
                    addOpenShiftResources(objects, isAppCatalog);
                    mergeParametersIntoMap(templateParameters, notNullList(template.getParameters()));
                    continue;
                }
            }

            KindAndName key = new KindAndName(item);
            HasMetadata old = openshiftDependencyResources.get(key);
            if (old != null && !isAppCatalog) {
                log.warn("Duplicate OpenShift resources for %s at %s and %s", key, getSourceUrlAnnotation(old), getSourceUrlAnnotation(item));
            }
            openshiftDependencyResources.put(key, item);
        }
    }

    private void mergeParametersIntoMap(Map<String, Parameter> targetMap, Iterable<Parameter> parameters) {
        for (Parameter parameter : parameters) {
            String name = parameter.getName();
            if (StringUtils.isNotBlank(name)) {
                Parameter old = targetMap.get(name);
                if (old != null) {
                    mergeParameters(old, parameter);
                } else {
                    targetMap.put(name, parameter);
                }
            }
        }
    }

    private void mergeParameters(Parameter current, Parameter other) {
        String value = other.getValue();
        if (StringUtils.isNotBlank(value)) {
            if (StringUtils.isBlank(current.getValue())) {
                current.setValue(value);
            }
        }
        String generate = other.getGenerate();
        if (StringUtils.isNotBlank(generate)) {
            if (StringUtils.isBlank(current.getGenerate())) {
                current.setGenerate(generate);
            }
        }
        String from = other.getFrom();
        if (StringUtils.isNotBlank(from)) {
            if (StringUtils.isBlank(current.getFrom())) {
                current.setFrom(from);
            }
        }
    }

    /**
     * Returns the OpenShift dependency for the given resource if there is one
     */
    public HasMetadata convertKubernetesItemToOpenShift(HasMetadata item) {
        KindAndName key = new KindAndName(item);
        HasMetadata answer = openshiftDependencyResources.get(key);
        if (answer == null && item instanceof Deployment) {
            key = new KindAndName("DeploymentConfig", KubernetesHelper.getName(item));
            answer = openshiftDependencyResources.get(key);
        }
        return answer;
    }

    public void addMissingParameters(Template template) {
        List<Parameter> parameters = template.getParameters();
        if (parameters == null) {
            parameters = new ArrayList<>();
        }
        Map<String, Parameter> map = new HashMap<>();
        for (Parameter parameter : parameters) {
            map.put(parameter.getName(), parameter);
        }
        mergeParametersIntoMap(map, parameters);
        for (Parameter parameter : map.values()) {
            if (!parameters.contains(parameter)) {
                parameters.add(parameter);
            }
        }
        template.setParameters(parameters);
    }

    public void addMissingResources(List<HasMetadata> objects) {
        Map<KindAndName, HasMetadata> itemMap = new HashMap<>();
        for (HasMetadata item : objects) {
            itemMap.put(new KindAndName(item), item);
        }

        // lets add any openshift specific dependencies (OAuthClient etc) which are not already added
        for (Map.Entry<KindAndName, HasMetadata> entry : openshiftDependencyResources.entrySet()) {
            KindAndName key = entry.getKey();
            HasMetadata dependency = entry.getValue();
            if (!itemMap.containsKey(key)) {
                objects.add(dependency);
            }
        }
    }

    private <T> List<T> notNullList(List<T> list) {
        if (list == null) {
            return Collections.EMPTY_LIST;
        } else {
            return list;
        }
    }
}
