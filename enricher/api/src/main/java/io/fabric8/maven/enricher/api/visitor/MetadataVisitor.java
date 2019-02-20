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
package io.fabric8.maven.enricher.api.visitor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.DaemonSetBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.batch.JobBuilder;
import io.fabric8.maven.core.config.MetaDataConfig;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.enricher.api.Enricher;
import io.fabric8.maven.enricher.api.Kind;
import io.fabric8.openshift.api.model.BuildBuilder;
import io.fabric8.openshift.api.model.BuildConfigBuilder;
import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import io.fabric8.openshift.api.model.ImageStreamBuilder;

import static io.fabric8.maven.enricher.api.util.ExtractorUtil.extractAnnotations;
import static io.fabric8.maven.enricher.api.util.ExtractorUtil.extractLabels;

/**
 * Visitor which adds labels and annotations
 *
 * @author roland
 * @since 02/05/16
 */
public abstract class MetadataVisitor<T> extends TypedVisitor<T> {


    private static ThreadLocal<ProcessorConfig> configHolder = new ThreadLocal<>();
    private final Map<String, String> labelsFromConfig;
    private final Map<String, String> annotationFromConfig;
    private List<Enricher> enrichers = null;

    private MetadataVisitor(ResourceConfig resourceConfig, List<Enricher> enrichers) {
        if (resourceConfig != null) {
            labelsFromConfig = getMapFromConfiguration(resourceConfig.getLabels(), getKind());
            annotationFromConfig = getMapFromConfiguration(resourceConfig.getAnnotations(), getKind());
        } else {
            labelsFromConfig = new HashMap<>();
            annotationFromConfig = new HashMap<>();
        }

        this.enrichers = enrichers;
    }

    public static void setProcessorConfig(ProcessorConfig config) {
        configHolder.set(config);
    }

    public static void clearProcessorConfig() {
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
        overlayMap(metadata.getLabels(), extractLabels(getProcessorConfig(), getKind(), enrichers));
    }

    private void updateAnnotations(ObjectMeta metadata) {
        overlayMap(metadata.getAnnotations(),annotationFromConfig);
        overlayMap(metadata.getAnnotations(), extractAnnotations(getProcessorConfig(), getKind(), enrichers));
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

        public PodTemplateSpecBuilderVisitor(ResourceConfig resourceConfig, List<Enricher> enrichers) {
            super(resourceConfig, enrichers);
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

        public ServiceBuilderVisitor(ResourceConfig resourceConfig, List<Enricher> enrichers) {
            super(resourceConfig, enrichers);
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
        public ReplicaSet(ResourceConfig resourceConfig, List<Enricher> enrichers) {
            super(resourceConfig, enrichers);
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
        public ReplicationControllerBuilderVisitor(ResourceConfig resourceConfig, List<Enricher> enrichers) {
            super(resourceConfig, enrichers);
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
        public DeploymentBuilderVisitor(ResourceConfig resourceConfig, List<Enricher> enrichers) {
            super(resourceConfig, enrichers);
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

    public static class DeploymentConfigBuilderVisitor extends MetadataVisitor<DeploymentConfigBuilder> {
        public DeploymentConfigBuilderVisitor(ResourceConfig resourceConfig, List<Enricher> enrichers) {
            super(resourceConfig, enrichers);
        }

        @Override
        protected Kind getKind() {
            return Kind.DEPLOYMENT;
        }

        @Override
        protected ObjectMeta getOrCreateMetadata(DeploymentConfigBuilder item) {
            return item.hasMetadata() ? item.buildMetadata() : item.withNewMetadata().endMetadata().buildMetadata();
        }
    }

    public static class DaemonSetBuilderVisitor extends MetadataVisitor<DaemonSetBuilder> {
        public DaemonSetBuilderVisitor(ResourceConfig resourceConfig, List<Enricher> enrichers) {
            super(resourceConfig, enrichers);
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
        public StatefulSetBuilderVisitor(ResourceConfig resourceConfig, List<Enricher> enrichers) {
            super(resourceConfig, enrichers);
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
        public JobBuilderVisitor(ResourceConfig resourceConfig, List<Enricher> enrichers) {
            super(resourceConfig, enrichers);
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

    public static class ImageStreamBuilderVisitor extends MetadataVisitor<ImageStreamBuilder> {
        public ImageStreamBuilderVisitor(ResourceConfig resourceConfig, List<Enricher> enrichers) {
            super(resourceConfig, enrichers);
        }

        @Override
        protected Kind getKind() {
            return Kind.IMAGESTREAM;
        }

        @Override
        protected ObjectMeta getOrCreateMetadata(ImageStreamBuilder item) {
            return item.hasMetadata() ? item.buildMetadata() : item.withNewMetadata().endMetadata().buildMetadata();
        }
    }

    public static class BuildConfigBuilderVisitor extends MetadataVisitor<BuildConfigBuilder> {
        public BuildConfigBuilderVisitor(ResourceConfig resourceConfig, List<Enricher> enrichers) {
            super(resourceConfig, enrichers);
        }

        @Override
        protected Kind getKind() {
            return Kind.BUILD_CONFIG;
        }

        @Override
        protected ObjectMeta getOrCreateMetadata(BuildConfigBuilder item) {
            return item.hasMetadata() ? item.buildMetadata() : item.withNewMetadata().endMetadata().buildMetadata();
        }
    }

    public static class BuildBuilderVisitor extends MetadataVisitor<BuildBuilder> {
        public BuildBuilderVisitor(ResourceConfig resourceConfig, List<Enricher> enrichers) {
            super(resourceConfig, enrichers);
        }

        @Override
        protected Kind getKind() {
            return Kind.BUILD;
        }

        @Override
        protected ObjectMeta getOrCreateMetadata(BuildBuilder item) {
            return item.hasMetadata() ? item.buildMetadata() : item.withNewMetadata().endMetadata().buildMetadata();
        }
    }
}