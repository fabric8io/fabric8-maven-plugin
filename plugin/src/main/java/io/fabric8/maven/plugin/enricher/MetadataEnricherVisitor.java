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

package io.fabric8.maven.plugin.enricher;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.builder.Visitor;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.extensions.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetBuilder;
import io.fabric8.maven.enricher.api.Kind;

/**
 * @author roland
 * @since 02/05/16
 */
public abstract class MetadataEnricherVisitor<T> implements Visitor<T> {

    private final EnricherManager enricher;


    private MetadataEnricherVisitor(EnricherManager enricher) {
        this.enricher = enricher;
    }

    @Override
    public void visit(T item) {
        ObjectMeta metadata = getOrCreateMetadata(item);
        overlayMap(metadata.getLabels(), enricher.extractLabels(getKind()));
        overlayMap(metadata.getAnnotations(), enricher.extractAnnotations(getKind()));
    }

    private void overlayMap(Map<String, String> targetMap, Map<String, String> enrichMap) {
        targetMap = getOrCreateMap(targetMap);
        enrichMap = getOrCreateMap(enrichMap);
        for (Map.Entry<String, String> entry : enrichMap.entrySet()) {
            if (!targetMap.containsKey(entry.getKey())) {
                targetMap.put(entry.getKey(), entry.getValue());
            }
        }
    }

    protected abstract Kind getKind();
    protected abstract ObjectMeta getOrCreateMetadata(T item);

    private Map<String, String> getOrCreateMap(Map<String, String> labels) {
        return labels != null ? labels : new HashMap<String, String>();
    }


    // =======================================================================================

    public static class PodTemplate extends MetadataEnricherVisitor<PodTemplateSpecBuilder> {

        public PodTemplate(EnricherManager enricher) {
            super(enricher);
        }

        @Override
        protected Kind getKind() {
            return Kind.POD_TEMPLATE;
        }

        @Override
        protected ObjectMeta getOrCreateMetadata(PodTemplateSpecBuilder item) {
            ObjectMeta ret = item.getMetadata();
            return ret == null ? item.withNewMetadata().endMetadata().getMetadata() : ret;
        }
    }

    public static class Service extends MetadataEnricherVisitor<ServiceBuilder> {

        public Service(EnricherManager enricher) {
            super(enricher);
        }

        @Override
        protected Kind getKind() {
            return Kind.SERVICE;
        }

        @Override
        protected ObjectMeta getOrCreateMetadata(ServiceBuilder item) {
            ObjectMeta ret = item.getMetadata();
            return ret == null ? item.withNewMetadata().endMetadata().getMetadata() : ret;
        }
    }

    public static class ReplicaSet extends MetadataEnricherVisitor<ReplicaSetBuilder> {
        public ReplicaSet(EnricherManager enricher) {
            super(enricher);
        }

        @Override
        protected Kind getKind() {
            return Kind.REPLICA_SET;
        }

        @Override
        protected ObjectMeta getOrCreateMetadata(ReplicaSetBuilder item) {
            ObjectMeta ret = item.getMetadata();
            return ret == null ? item.withNewMetadata().endMetadata().getMetadata() : ret;
        }
    }

    public static class ReplicationController extends MetadataEnricherVisitor<ReplicationControllerBuilder> {
        public ReplicationController(EnricherManager enricher) {
            super(enricher);
        }

        @Override
        protected Kind getKind() {
            return Kind.REPLICATION_CONTROLLER;
        }

        @Override
        protected ObjectMeta getOrCreateMetadata(ReplicationControllerBuilder item) {
            ObjectMeta ret = item.getMetadata();
            return ret == null ? item.withNewMetadata().endMetadata().getMetadata() : ret;
        }
    }

    public static class Deployment extends MetadataEnricherVisitor<DeploymentBuilder> {
        public Deployment(EnricherManager enricher) {
            super(enricher);
        }

        @Override
        protected Kind getKind() {
            return Kind.DEPLOYMENT;
        }

        @Override
        protected ObjectMeta getOrCreateMetadata(DeploymentBuilder item) {
            ObjectMeta ret = item.getMetadata();
            return ret == null ? item.withNewMetadata().endMetadata().getMetadata() : ret;
        }
    }
}
