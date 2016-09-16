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

package io.fabric8.maven.enricher.standard;

import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.extensions.*;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;

import static io.fabric8.maven.core.util.KubernetesResourceUtil.extractContainerName;
import static io.fabric8.utils.Strings.isNullOrBlank;

/**
 * Merge in image configuration like the image name into ReplicaSet and ReplicationController's
 * Pod specification.
 *
 * <ul>
 *     <li>The full image name is set as <code>image</code></li>
 *     <li>An image alias is set as <code>name</code></li>
 *     <li>The pull policy <code>imagePullPolicy</code> is set according to the given configuration. If no
 *         configuration is set, the default is "IfNotPresent" for release versions, and "Always" for snapshot versions</li>
 * </ul>
 *
 * Any already configured container in the pod spec is updated if the property is not set.
 *
 * @author roland
 * @since 25/05/16
 */
public class ImageEnricher extends BaseEnricher {

    public ImageEnricher(EnricherContext buildContext) {
        super(buildContext, "fmp-image");
    }


    // Available configuration keys
    private enum Config implements Configs.Key {
        // What pull policy to use when fetching images
        pullPolicy;

        public String def() { return d; } protected String d;
    }

    @Override
    public void addMissingResources(KubernetesListBuilder builder) {

        // Ensure that all contoller have template specs
        ensureTemplateSpecs(builder);

        // Update containers in template specs
        updateContainers(builder);
    }

    // ============================================================================================================

    private void ensureTemplateSpecs(KubernetesListBuilder builder) {
        ensureTemplateSpecsInReplicationControllers(builder);
        ensureTemplateSpecsInRelicaSet(builder);
        ensureTemplateSpecsInDeployments(builder);
    }

    private void ensureTemplateSpecsInReplicationControllers(KubernetesListBuilder builder) {
        builder.accept(new TypedVisitor<ReplicationControllerBuilder>() {
            @Override
            public void visit(ReplicationControllerBuilder item) {
                ReplicationControllerFluent.SpecNested<ReplicationControllerBuilder> spec =
                    item.getSpec() == null ? item.withNewSpec() : item.editSpec();
                ReplicationControllerSpecFluent.TemplateNested<ReplicationControllerFluent.SpecNested<ReplicationControllerBuilder>>
                    template =
                    spec.getTemplate() == null ? spec.withNewTemplate() : spec.editTemplate();
                template.endTemplate().endSpec();
            }
        });
    }

    private void ensureTemplateSpecsInRelicaSet(KubernetesListBuilder builder) {
        builder.accept(new TypedVisitor<ReplicaSetBuilder>() {
            @Override
            public void visit(ReplicaSetBuilder item) {
                ReplicaSetFluent.SpecNested<ReplicaSetBuilder> spec =
                    item.getSpec() == null ? item.withNewSpec() : item.editSpec();
                ReplicaSetSpecFluent.TemplateNested<ReplicaSetFluent.SpecNested<ReplicaSetBuilder>> template =
                    spec.getTemplate() == null ? spec.withNewTemplate() : spec.editTemplate();
                template.endTemplate().endSpec();
            }
        });
    }

    private void ensureTemplateSpecsInDeployments(KubernetesListBuilder builder) {
        builder.accept(new TypedVisitor<DeploymentBuilder>() {
            @Override
            public void visit(DeploymentBuilder item) {
                DeploymentFluent.SpecNested<DeploymentBuilder> spec =
                    item.getSpec() == null ? item.withNewSpec() : item.editSpec();
                DeploymentSpecFluent.TemplateNested<DeploymentFluent.SpecNested<DeploymentBuilder>> template =
                    spec.getTemplate() == null ? spec.withNewTemplate() : spec.editTemplate();
                template.endTemplate().endSpec();
            }
        });
    }


    // ============================================================================================================

    private void updateContainers(KubernetesListBuilder builder) {
        builder.accept(new TypedVisitor<PodTemplateSpecBuilder>() {
            @Override
            public void visit(PodTemplateSpecBuilder templateBuilder) {
                PodTemplateSpecFluent.SpecNested<PodTemplateSpecBuilder> podSpec =
                    templateBuilder.getSpec() == null ? templateBuilder.withNewSpec() : templateBuilder.editSpec();

                List<Container> containers = podSpec.getContainers();
                if (containers == null) {
                    containers = new ArrayList<Container>();
                }
                mergeImageConfigurationWithContainerSpec(containers);
                podSpec.withContainers(containers).endSpec();
            }
        });
    }

    // Add missing information to the given containers as found
    // configured
    private void mergeImageConfigurationWithContainerSpec(List<Container> containers) {
        int idx = 0;
        List<ImageConfiguration> images = getImages();
        if (images.isEmpty()) {
            log.warn("No resolved images!");
        }
        for (ImageConfiguration imageConfiguration : images) {
            Container container;
            if (idx < containers.size()) {
                container = containers.get(idx);
            } else {
                // Pad with new containers if missing
                container = new Container();
                containers.add(container);
            }
            // Set various parameters if not set.
            if (isNullOrBlank(container.getImagePullPolicy())) {
                String policy = getConfig(Config.pullPolicy);
                if (policy == null) {
                    policy = getProject().getVersion().endsWith("-SNAPSHOT") ? "Always" : "IfNotPresent";
                }
                container.setImagePullPolicy(policy);
            }
            if (isNullOrBlank(container.getImage())) {
                log.info("Setting image %s",imageConfiguration.getName());
                container.setImage(imageConfiguration.getName());
            }
            if (isNullOrBlank(container.getName())) {
                String containerName = extractContainerName(getProject(), imageConfiguration);
                log.info("Setting container name %s",containerName);
                container.setName(containerName);
            }
            idx++;
        }
    }

}
