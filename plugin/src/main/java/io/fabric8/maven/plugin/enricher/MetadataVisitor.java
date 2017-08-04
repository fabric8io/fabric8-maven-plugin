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
import java.util.Properties;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.extensions.*;
import io.fabric8.maven.core.config.MetaDataConfig;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.config.ResourceConfig;
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
    private final Map<String, String> labelsFromConfig;
    private final Map<String, String> annotationFromConfig;

    private MetadataVisitor(ResourceConfig resourceConfig, EnricherManager enricherManager) {
        this.enricherManager = enricherManager;
        if (resourceConfig != null) {
            labelsFromConfig = getMapFromConfiguration(resourceConfig.getLabels(), getKind());
            annotationFromConfig = getMapFromConfiguration(resourceConfig.getAnnotations(), getKind());
        } else {
            labelsFromConfig = new HashMap<>();
            annotationFromConfig = new HashMap<>();
        }
    }

    static void setProcessorConfig(ProcessorConfig config) {
        configHolder.set(config);
    }

    static void clearProcessorConfig() {
        configHolder.set(null);
    }

    private ProcessorConfig getProcessorConfig() {
        ProcessorConfig config = configHolder.get();
        if (config == null) {
            throw new IllegalArgumentException("No ProcessorConfig set");
        }
        return config;
    }

    public void visit(T item) {
        ProcessorConfig config = getProcessorConfig();
        ObjectMeta metadata = getOrCreateMetadata(item);
        updateLabels(metadata);
        updateAnnotations(metadata);
    }

    private void updateLabels(ObjectMeta metadata) {
        overlayMap(metadata.getLabels(),labelsFromConfig);
        overlayMap(metadata.getLabels(),enricherManager.extractLabels(getProcessorConfig(), getKind()));
    }

    private void updateAnnotations(ObjectMeta metadata) {
        overlayMap(metadata.getAnnotations(),annotationFromConfig);
        overlayMap(metadata.getAnnotations(), enricherManager.extractAnnotations(getProcessorConfig(), getKind()));
    }

    private Map<String, String> getMapFromConfiguration(MetaDataConfig config, Kind kind) {
        if (config == null) {
            return new HashMap<>();
        }
        Map<String, String> ret;
        if (kind == Kind.SERVICE) {
            ret = propertiesToMap(config.getService());
        } else if (kind == Kind.DEPLOYMENT || kind == Kind.DEPLOYMENT_CONFIG) {
            ret = propertiesToMap(config.getDeployment());
        } else if (kind == Kind.REPLICATION_CONTROLLER || kind == Kind.REPLICA_SET) {
            ret = propertiesToMap(config.getReplicaSet());
        } else if (kind == Kind.POD_SPEC) {
            ret = propertiesToMap(config.getPod());
        } else {
            ret = new HashMap<>();
        }
        if (config.getAll() != null) {
            ret.putAll(propertiesToMap(config.getAll()));
        }
        return ret;
    }

    private Map<String, String> propertiesToMap(Properties properties) {
        Map<String, String> propertyMap = new HashMap<>();
        if(properties != null) {
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                propertyMap.put(entry.getKey().toString(), entry.getValue().toString());
            }
        }
        return propertyMap;
    }


    private void overlayMap(Map<String, String> targetMap, Map<String, String> enrichMap) {
        targetMap = ensureMap(targetMap);
        enrichMap = ensureMap(enrichMap);
        for (Map.Entry<String, String> entry : enrichMap.entrySet()) {
            if (!targetMap.containsKey(entry.getKey())) {
                targetMap.put(entry.getKey(), entry.getValue());
            }
        }
    }

    protected abstract Kind getKind();
    protected abstract ObjectMeta getOrCreateMetadata(T item);

    private Map<String, String> ensureMap(Map<String, String> labels) {
        return labels != null ? labels : new HashMap<String, String>();
    }


    // =======================================================================================

    public static class PodTemplateSpecBuilderVisitor extends MetadataVisitor<PodTemplateSpecBuilder> {

        PodTemplateSpecBuilderVisitor(ResourceConfig resourceConfig, EnricherManager enricher) {
            super(resourceConfig, enricher);
        }

        @Override
        protected Kind getKind() {
            return Kind.POD_SPEC;
        }

        @Override
        protected ObjectMeta getOrCreateMetadata(PodTemplateSpecBuilder item) {
            return item.hasMetadata() ? item.buildMetadata() : item.withNewMetadata().endMetadata().buildMetadata();
        }
    }

    public static class ServiceBuilderVisitor extends MetadataVisitor<ServiceBuilder> {

        ServiceBuilderVisitor(ResourceConfig resourceConfig, EnricherManager enricher) {
            super(resourceConfig, enricher);
        }

        @Override
        protected Kind getKind() {
            return Kind.SERVICE;
        }

        @Override
        protected ObjectMeta getOrCreateMetadata(ServiceBuilder item) {
            return item.hasMetadata() ? item.buildMetadata() : item.withNewMetadata().endMetadata().buildMetadata();
        }
    }

    public static class ReplicaSet extends MetadataVisitor<ReplicaSetBuilder> {
        ReplicaSet(ResourceConfig resourceConfig, EnricherManager enricher) {
            super(resourceConfig, enricher);
        }

        @Override
        protected Kind getKind() {
            return Kind.REPLICA_SET;
        }

        @Override
        protected ObjectMeta getOrCreateMetadata(ReplicaSetBuilder item) {
            return item.hasMetadata() ? item.buildMetadata() : item.withNewMetadata().endMetadata().buildMetadata();
        }
    }

    public static class ReplicationControllerBuilderVisitor extends MetadataVisitor<ReplicationControllerBuilder> {
        ReplicationControllerBuilderVisitor(ResourceConfig resourceConfig, EnricherManager enricher) {
            super(resourceConfig, enricher);
        }

        @Override
        protected Kind getKind() {
            return Kind.REPLICATION_CONTROLLER;
        }

        @Override
        protected ObjectMeta getOrCreateMetadata(ReplicationControllerBuilder item) {
            return item.hasMetadata() ? item.buildMetadata() : item.withNewMetadata().endMetadata().buildMetadata();
        }
    }

    public static class DeploymentBuilderVisitor extends MetadataVisitor<DeploymentBuilder> {
        DeploymentBuilderVisitor(ResourceConfig resourceConfig, EnricherManager enricher) {
            super(resourceConfig, enricher);
        }

        @Override
        protected Kind getKind() {
            return Kind.DEPLOYMENT;
        }

        @Override
        protected ObjectMeta getOrCreateMetadata(DeploymentBuilder item) {
            return item.hasMetadata() ? item.buildMetadata() : item.withNewMetadata().endMetadata().buildMetadata();
        }
    }

    public static class DaemonSetBuilderVisitor extends MetadataVisitor<DaemonSetBuilder> {
        DaemonSetBuilderVisitor(ResourceConfig resourceConfig, EnricherManager enricher) {
            super(resourceConfig, enricher);
        }

        @Override
        protected Kind getKind() {
            return Kind.DAEMON_SET;
        }

        @Override
        protected ObjectMeta getOrCreateMetadata(DaemonSetBuilder item) {
            return item.hasMetadata() ? item.buildMetadata() : item.withNewMetadata().endMetadata().buildMetadata();
        }
    }

    public static class StatefulSetBuilderVisitor extends MetadataVisitor<StatefulSetBuilder> {
        StatefulSetBuilderVisitor(ResourceConfig resourceConfig, EnricherManager enricher) {
            super(resourceConfig, enricher);
        }

        @Override
        protected Kind getKind() {
            return Kind.STATEFUL_SET;
        }

        @Override
        protected ObjectMeta getOrCreateMetadata(StatefulSetBuilder item) {
            return item.hasMetadata() ? item.buildMetadata() : item.withNewMetadata().endMetadata().buildMetadata();
        }
    }

    public static class JobBuilderVisitor extends MetadataVisitor<JobBuilder> {
        JobBuilderVisitor(ResourceConfig resourceConfig, EnricherManager enricher) {
            super(resourceConfig, enricher);
        }

        @Override
        protected Kind getKind() {
            return Kind.JOB;
        }

        @Override
        protected ObjectMeta getOrCreateMetadata(JobBuilder item) {
            return item.hasMetadata() ? item.buildMetadata() : item.withNewMetadata().endMetadata().buildMetadata();
        }
    }

    public static class SecretBuilderVisitor extends MetadataVisitor<SecretBuilder> {
        SecretBuilderVisitor(ResourceConfig resourceConfig, EnricherManager enricher) { super(resourceConfig, enricher);}

        @Override
        protected Kind getKind() {
            return Kind.SECRET;
        }

        @Override
        protected ObjectMeta getOrCreateMetadata(SecretBuilder item) {
            return item.hasMetadata() ? item.buildMetadata() : item.withNewMetadata().endMetadata().buildMetadata();
        }
    }
}
