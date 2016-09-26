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

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.extensions.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetBuilder;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.enricher.api.Kind;

/**
 * Visitor which adds labels and annotations
 *
 * @author roland
 * @since 02/05/16
 */
public abstract class MetadataVisitor<T> extends TypedVisitor<T> {

    private final EnricherManager enricherManager;

    private static ThreadLocal<ProcessorConfig> configHolder = new ThreadLocal<>();

    private MetadataVisitor(EnricherManager enricherManager) {
        this.enricherManager = enricherManager;
    }

    public static void setProcessorConfig(ProcessorConfig config) {
        configHolder.set(config);
    }

    public static void clearProcessorConfig() {
        configHolder.set(null);
    }

    public void visit(T item) {
        ProcessorConfig config = configHolder.get();
        if (config == null) {
            throw new IllegalArgumentException("No ProcessorConfig set");
        }
        ObjectMeta metadata = getOrCreateMetadata(item);
        overlayMap(metadata.getLabels(), enricherManager.extractLabels(config, getKind()));
        overlayMap(metadata.getAnnotations(), enricherManager.extractAnnotations(config, getKind()));
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

    public static class PodSpec extends MetadataVisitor<PodTemplateSpecBuilder> {

        public PodSpec(EnricherManager enricher) {
            super(enricher);
        }

        @Override
        protected Kind getKind() {
            return Kind.POD_SPEC;
        }

        @Override
        protected ObjectMeta getOrCreateMetadata(PodTemplateSpecBuilder item) {
            ObjectMeta ret = item.getMetadata();
            return ret == null ? item.withNewMetadata().endMetadata().getMetadata() : ret;
        }
    }

    public static class Service extends MetadataVisitor<ServiceBuilder> {

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

    public static class ReplicaSet extends MetadataVisitor<ReplicaSetBuilder> {
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

    public static class ReplicationController extends MetadataVisitor<ReplicationControllerBuilder> {
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

    public static class Deployment extends MetadataVisitor<DeploymentBuilder> {
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
