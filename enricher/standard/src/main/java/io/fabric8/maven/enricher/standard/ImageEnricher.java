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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecFluent;
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder;
import io.fabric8.kubernetes.api.model.ReplicationControllerFluent;
import io.fabric8.kubernetes.api.model.ReplicationControllerSpecFluent;
import io.fabric8.kubernetes.api.model.extensions.DaemonSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.DaemonSetFluent;
import io.fabric8.kubernetes.api.model.extensions.DaemonSetSpecFluent;
import io.fabric8.kubernetes.api.model.extensions.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.extensions.DeploymentFluent;
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpecFluent;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetFluent;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetSpecFluent;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetFluent;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetSpecFluent;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.KubernetesResourceUtil;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import io.fabric8.openshift.api.model.DeploymentConfigFluent;
import io.fabric8.openshift.api.model.DeploymentConfigSpecFluent;
import io.fabric8.utils.Strings;

import static io.fabric8.maven.core.util.KubernetesResourceUtil.extractContainerName;

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
        if (!hasImageConfiguration()) {
            log.verbose("No images resolved. Skipping ...");
            return;
        }

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
        ensureTemplateSpecsInDaemonSet(builder);
        ensureTemplateSpecsInStatefulSet(builder);
        ensureTemplateSpecsInDeploymentConfig(builder);
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

    private void ensureTemplateSpecsInDaemonSet(KubernetesListBuilder builder) {
        builder.accept(new TypedVisitor<DaemonSetBuilder>() {
            @Override
            public void visit(DaemonSetBuilder item) {
                DaemonSetFluent.SpecNested<DaemonSetBuilder> spec =
                        item.getSpec() == null ? item.withNewSpec() : item.editSpec();
                DaemonSetSpecFluent.TemplateNested<DaemonSetFluent.SpecNested<DaemonSetBuilder>> template =
                        spec.getTemplate() == null ? spec.withNewTemplate() : spec.editTemplate();
                template.endTemplate().endSpec();
            }
        });
    }

    private void ensureTemplateSpecsInStatefulSet(KubernetesListBuilder builder) {
        builder.accept(new TypedVisitor<StatefulSetBuilder>() {
            @Override
            public void visit(StatefulSetBuilder item) {
                StatefulSetFluent.SpecNested<StatefulSetBuilder> spec =
                        item.getSpec() == null ? item.withNewSpec() : item.editSpec();
                StatefulSetSpecFluent.TemplateNested<StatefulSetFluent.SpecNested<StatefulSetBuilder>> template =
                        spec.getTemplate() == null ? spec.withNewTemplate() : spec.editTemplate();
                template.endTemplate().endSpec();
            }
        });
    }

    private void ensureTemplateSpecsInDeploymentConfig(KubernetesListBuilder builder) {
        builder.accept(new TypedVisitor<DeploymentConfigBuilder>() {
            @Override
            public void visit(DeploymentConfigBuilder item) {
                DeploymentConfigFluent.SpecNested<DeploymentConfigBuilder> spec =
                        item.getSpec() == null ? item.withNewSpec() : item.editSpec();
                DeploymentConfigSpecFluent.TemplateNested<DeploymentConfigFluent.SpecNested<DeploymentConfigBuilder>> template =
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
        List<ImageConfiguration> images = getImages();
        int idx = 0;
        for (ImageConfiguration imageConfiguration : images) {
            Container container = getContainer(idx, containers);
            mergeImagePullPolicy(imageConfiguration, container);
            mergeImage(imageConfiguration, container);
            mergeContainerName(imageConfiguration, container);
            mergeEnvVariables(container);
            idx++;
        }
    }

    private Container getContainer(int idx, List<Container> containers) {
        Container container;
        if (idx < containers.size()) {
            container = containers.get(idx);
        } else {
            // Pad with new containers if missing
            container = new Container();
            containers.add(container);
        }
        return container;
    }

    private void mergeContainerName(ImageConfiguration imageConfiguration, Container container) {
        if (Strings.isNullOrBlank(container.getName())) {
            String containerName = extractContainerName(getProject(), imageConfiguration);
            log.verbose("Setting container name %s",containerName);
            container.setName(containerName);
        }
    }

    private void mergeImage(ImageConfiguration imageConfiguration, Container container) {
        if (Strings.isNullOrBlank(container.getImage())) {
            String prefix = "";
            if (Strings.isNotBlank(imageConfiguration.getRegistry())) {
                log.verbose("Using registry %s for the image", imageConfiguration.getRegistry());
                prefix = imageConfiguration.getRegistry() + "/";
            }
            String imageFullName = prefix + imageConfiguration.getName();
            log.verbose("Setting image %s", imageFullName);
            container.setImage(imageFullName);
        }
    }

    private void mergeImagePullPolicy(ImageConfiguration imageConfiguration, Container container) {
        if (Strings.isNullOrBlank(container.getImagePullPolicy())) {
            String policy = getConfig(Config.pullPolicy);
            if (policy == null) {
                policy = "IfNotPresent";
                String imageName = imageConfiguration.getName();
                if (Strings.isNotBlank(imageName) && imageName.endsWith(":latest")) {
                    policy = "Always";
                }
            }
            container.setImagePullPolicy(policy);
        }
    }

    private void mergeEnvVariables(Container container) {
        List<EnvVar> env = container.getEnv();
        if (env == null) {
            env = new LinkedList<>();
            container.setEnv(env);
        }

        ResourceConfig resource = getContext().getResources();
        Map<String, String> userEnv = resource != null ? resource.getEnv() : null;
        if (userEnv != null) {
            for(Map.Entry<String, String> entry : userEnv.entrySet()) {
                EnvVar existingVariable = KubernetesResourceUtil.setEnvVarNoOverride(env, entry.getKey(), entry.getValue());
                if (existingVariable != null) {
                    String actualValue = existingVariable.getValue();
                    if (actualValue == null) {
                        actualValue = "retrieved using the downward API";
                    }
                    log.warn("Environment variable %s will not be overridden: trying to set the value %s, but its actual value will be %s", entry.getKey(), entry.getValue(), actualValue);
                }
            }
        }
    }
}
