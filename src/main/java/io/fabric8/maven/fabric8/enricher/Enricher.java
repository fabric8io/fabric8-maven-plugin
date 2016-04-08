package io.fabric8.maven.fabric8.enricher;
/*
 * 
 * Copyright 2016 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Map;

import io.fabric8.kubernetes.api.model.KubernetesList;

/**
 * Interface describing enrichers which add to kubernetes descriptors
 *
 * @author roland
 * @since 01/04/16
 */
public interface Enricher {

    /**
     * Unique name of the enricher
     *
     * @return enricher name
     */
    String getName();

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
     * Final customization of the overall resource
     * descriptor
     *
     * @param descriptor used to customize
     */
    void customize(KubernetesList descriptor);
}
