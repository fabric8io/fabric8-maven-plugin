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

        builder.accept(new TypedVisitor<ReplicationControllerBuilder>() {
            @Override
            public void visit(ReplicationControllerBuilder item) {
                getOrCreateContainerList(item);
            }
        });

        builder.accept(new TypedVisitor<ReplicaSetBuilder>() {
            @Override
            public void visit(ReplicaSetBuilder item) {
                getOrCreateContainerList(item);
            }
        });

        builder.accept(new TypedVisitor<DeploymentBuilder>() {
            @Override
            public void visit(DeploymentBuilder item) {
                getOrCreateContainerList(item);
            }
        });
    }

    private List<Container> getOrCreateContainerList(ReplicaSetBuilder rs) {
        ReplicaSetSpec spec = getOrCreateReplicaSetSpec(rs);
        PodTemplateSpec template = getOrCreatePodTemplateSpec(spec);
        return getOrCreateContainerList(template);
    }

    private List<Container> getOrCreateContainerList(ReplicationControllerBuilder rc) {
        ReplicationControllerSpec spec = getOrCreateReplicationControllerSpec(rc);
        PodTemplateSpec template = getOrCreatePodTemplateSpec(spec);
        return getOrCreateContainerList(template);
    }

    private List<Container> getOrCreateContainerList(DeploymentBuilder d) {
        DeploymentSpec spec = getOrCreateDeploymentSpec(d);
        PodTemplateSpec template = getOrCreatePodTemplateSpec(spec);
        return getOrCreateContainerList(template);
    }

    // ===================================================================================

    private ReplicaSetSpec getOrCreateReplicaSetSpec(ReplicaSetBuilder rs) {
        ReplicaSetSpec spec = rs.getSpec();
        if (spec == null) {
            spec = new ReplicaSetSpec();
            rs.withSpec(spec);
        }
        return spec;
    }

    private ReplicationControllerSpec getOrCreateReplicationControllerSpec(ReplicationControllerBuilder rc) {
        ReplicationControllerSpec spec = rc.getSpec();
        if (spec == null) {
            spec = new ReplicationControllerSpec();
            rc.withSpec(spec);
        }
        return spec;
    }

    private DeploymentSpec getOrCreateDeploymentSpec(DeploymentBuilder d) {
        DeploymentSpec spec = d.getSpec();
        if (spec == null) {
            spec = new DeploymentSpec();
            d.withSpec(spec);
        }
        return spec;
    }

    // ==============================================================================================

    private PodTemplateSpec getOrCreatePodTemplateSpec(ReplicaSetSpec spec) {
        PodTemplateSpec template = spec.getTemplate();
        if (template == null) {
            template = new PodTemplateSpec();
            spec.setTemplate(template);
        }
        return template;
    }

    private PodTemplateSpec getOrCreatePodTemplateSpec(ReplicationControllerSpec spec) {
        PodTemplateSpec template = spec.getTemplate();
        if (template == null) {
            template = new PodTemplateSpec();
            spec.setTemplate(template);
        }
        return template;
    }

    private PodTemplateSpec getOrCreatePodTemplateSpec(DeploymentSpec spec) {
        PodTemplateSpec template = spec.getTemplate();
        if (template == null) {
            template = new PodTemplateSpec();
            spec.setTemplate(template);
        }
        return template;
    }

    // ==============================================================================================

    private List<Container> getOrCreateContainerList(PodTemplateSpec template) {
        PodSpec podSpec = getOrCreatePodSpec(template);
        List<Container> containers = getOrCreateContainers(podSpec);
        mergeImageConfigurationWithContainerSpec(containers);
        return containers;
    }

    private PodSpec getOrCreatePodSpec(PodTemplateSpec template) {
        PodSpec podSpec = template.getSpec();
        if (podSpec == null) {
            podSpec = new PodSpec();
            template.setSpec(podSpec);
        }
        return podSpec;
    }

    private List<Container> getOrCreateContainers(PodSpec podSpec) {
        List<Container> containers = podSpec.getContainers();
        if (containers == null) {
            containers = new ArrayList<Container>();
            podSpec.setContainers(containers);
        }
        return containers;
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
