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

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.openshift.api.model.Parameter;
import io.fabric8.openshift.api.model.Template;
import io.fabric8.utils.Strings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.fabric8.kubernetes.api.KubernetesHelper.getName;
import static io.fabric8.kubernetes.api.KubernetesHelper.getOrCreateAnnotations;
import static io.fabric8.maven.core.util.Constants.APP_CATALOG_ANNOTATION;
import static io.fabric8.maven.core.util.KubernetesResourceUtil.location;
import static io.fabric8.maven.core.util.KubernetesResourceUtil.setLocation;
import static io.fabric8.utils.Lists.notNullList;

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
                    String location = location(template);
                    if (Strings.isNotBlank(location)) {
                        for (HasMetadata object : objects) {
                            setLocation(object, location);
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
                log.warn("Duplicate OpenShift resources for " + key + " at " + location(old) + " and " + location(item));
            }
            openshiftDependencyResources.put(key, item);
        }
    }

    private void mergeParametersIntoMap(Map<String, Parameter> parameterMap, Iterable<Parameter> parameters) {
        for (Parameter parameter : parameters) {
            String name = parameter.getName();
            if (Strings.isNotBlank(name)) {
                Parameter old = parameterMap.get(name);
                if (old != null) {
                    mergeParameters(old, parameter);
                } else {
                    parameterMap.put(name, parameter);
                }
            }
        }
    }

    private void mergeParameters(Parameter current, Parameter other) {
        String value = other.getValue();
        if (Strings.isNotBlank(value)) {
            if (Strings.isNullOrBlank(current.getValue())) {
                current.setValue(value);
            }
        }
        String generate = other.getGenerate();
        if (Strings.isNotBlank(generate)) {
            if (Strings.isNullOrBlank(current.getGenerate())) {
                current.setGenerate(generate);
            }
        }
        String from = other.getFrom();
        if (Strings.isNotBlank(from)) {
            if (Strings.isNullOrBlank(current.getFrom())) {
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
            key = new KindAndName("DeploymentConfig", getName(item));
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
}
