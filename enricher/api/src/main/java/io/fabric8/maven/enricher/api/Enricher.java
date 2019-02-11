/**
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
package io.fabric8.maven.enricher.api;

import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.core.config.Named;
import io.fabric8.maven.core.config.PlatformMode;

import java.util.List;
import java.util.Map;

/**
 * Interface describing enrichers which add to kubernetes descriptors
 *
 * @author roland
 * @since 01/04/16
 */
public interface Enricher extends Named {

    /**
     * Get labels to add too objects
     *
     * @param kind for which type to get the labels
     * @return map of additional labels
     */
    Map<String, String> getLabels(Kind kind);

    /**
     * Return annotations to add
     *
     * @param kind the kind of object to add
     * @return map of annotations
     */
    Map<String, String> getAnnotations(Kind kind);

    /**
     * Get the selector for a service or replica set / replication controller
     *
     * @param kind get the selector map
     * @return selector
     */
    Map<String,String> getSelector(Kind kind);

    /**
     * Add default resources when they are missing. Each enricher should be responsible
     * for a certain kind of resource and should detect whether a default resource
     * should be added. This should be done defensive, so that when an object is
     * already set it must not be overwritten. This method is only called on resources which are
     * associated with the artefact to build. This is determined that the resource is named like the artifact
     * to build.
     *
     * @param builder the build to examine and add to
     */
    void addMissingResources(PlatformMode platformMode, KubernetesListBuilder builder);

    /**
     * Final customization of the overall resource descriptor. Fine tuning happens here.
     *
     * @param builder list to customer used to customize
     */
    void adapt(PlatformMode platformMode, KubernetesListBuilder builder);

    void addMetadata(PlatformMode platformMode, KubernetesListBuilder builder, List<Enricher> enrichers);
}
