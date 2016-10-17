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

package io.fabric8.maven.plugin.mojo.develop;


import io.fabric8.kubernetes.api.Controller;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerSpec;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpec;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.maven.core.access.ClusterAccess;
import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.util.GoalFinder;
import io.fabric8.maven.core.util.KubernetesResourceUtil;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.ImageNameFormatter;
import io.fabric8.maven.plugin.generator.GeneratorManager;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigSpec;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.utils.Files;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static io.fabric8.kubernetes.api.KubernetesHelper.getKind;
import static io.fabric8.kubernetes.api.KubernetesHelper.getName;
import static io.fabric8.maven.plugin.mojo.build.ApplyMojo.DEFAULT_KUBERNETES_MANIFEST;
import static io.fabric8.maven.plugin.mojo.build.ApplyMojo.DEFAULT_OPENSHIFT_MANIFEST;
import static io.fabric8.maven.plugin.mojo.build.ApplyMojo.loadResources;

/**
 * Used to automatically rebuild Docker images and restart containers in case of updates.
 */
@Mojo(name = "watch", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.COMPILE)
public class WatchMojo extends io.fabric8.maven.docker.WatchMojo {

    @Parameter
    ProcessorConfig generator;

    /**
     * To skip over the execution of the goal
     */
    @Parameter(property = "fabric8.skip", defaultValue = "false")
    protected boolean skip;
    /**
     * The generated kubernetes YAML file
     */
    @Parameter(property = "fabric8.kubernetesManifest", defaultValue = DEFAULT_KUBERNETES_MANIFEST)
    private File kubernetesManifest;
    /**
     * The generated openshift YAML file
     */
    @Parameter(property = "fabric8.openshiftManifest", defaultValue = DEFAULT_OPENSHIFT_MANIFEST)
    private File openshiftManifest;
    /**
     * Whether to perform a Kubernetes build (i.e. agains a vanilla Docker daemon) or
     * an OpenShift build (with a Docker build against the OpenShift API server.
     */
    @Parameter(property = "fabric8.mode")
    private PlatformMode mode = PlatformMode.auto;
    /**
     * OpenShift build mode when an OpenShift build is performed.
     * Can be either "s2i" for an s2i binary build mode or "docker" for a binary
     * docker mode.
     */
    @Parameter(property = "fabric8.build.strategy")
    private OpenShiftBuildStrategy buildStrategy = OpenShiftBuildStrategy.s2i;

    /**
     * Namespace on which to operate
     */
    @Parameter(property = "fabric8.namespace")
    private String namespace;

    // Used for determining which mojos are called during a run
    @Component
    protected GoalFinder goalFinder;

    private ClusterAccess clusterAccess;
    private KubernetesClient kubernetes;
    private Controller controller;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if( skip ) {
            return;
        }
        super.execute();
    }

    @Override
    protected synchronized void executeInternal(ServiceHub hub) throws DockerAccessException, MojoExecutionException {
        clusterAccess = new ClusterAccess(namespace);
        kubernetes = clusterAccess.createKubernetesClient();
        controller = new Controller(kubernetes);

        URL masterUrl = kubernetes.getMasterUrl();
        KubernetesResourceUtil.validateKubernetesMasterUrl(masterUrl);

        super.executeInternal(hub);
    }

    @Override
    public List<ImageConfiguration> customizeConfig(List<ImageConfiguration> configs) {
        if (generator == null) {
            // TODO discover the generators - not sure how yet ;)....
            List<String> includes = Arrays.asList("spring-boot");
            Set<String> excludes = new HashSet(Arrays.asList());
            Map<String, TreeMap> config = new HashMap<>();
            generator = new ProcessorConfig(includes, excludes, config);
        }
        try {
            return GeneratorManager.generate(configs, generator, project, session, goalFinder, "fabric8:watch", log, mode, buildStrategy, false);
        } catch (MojoExecutionException e) {
            throw new IllegalArgumentException("Cannot extract generator config: " + e, e);
        }
    }

    @Override
    protected void buildImage(ServiceHub hub, ImageConfiguration imageConfig) throws DockerAccessException, MojoExecutionException {
        String imageName = imageConfig.getName();
        // lets regenerate the label
        try {
            String imagePrefix = getImagePrefix(imageName);
                imageName = imagePrefix + "%t";
                ImageNameFormatter formatter = new ImageNameFormatter(project, new Date());
                imageName = formatter.format(imageName);
            imageConfig.setName(imageName);
            log.info("build new image: " + imageConfig.getName());
        } catch (Exception e) {
            log.error("Caught: " + e, e);
        }
        super.buildImage(hub, imageConfig);

    }

    private String getImagePrefix(String imageName) throws MojoExecutionException {
        String imagePrefix = null;
        int idx = imageName.lastIndexOf(':');
        if (idx < 0) {
            throw new MojoExecutionException("No ':' in the image name:  " + imageName);
        } else {
            imagePrefix = imageName.substring(0, idx + 1);
        }
        return imagePrefix;
    }

    @Override
    protected void restartContainer(ServiceHub hub, ImageWatcher watcher) throws DockerAccessException, MojoExecutionException, MojoFailureException {
        ImageConfiguration imageConfig = watcher.getImageConfiguration();
        String imageName = imageConfig.getName();
        try {
            File manifest;
            if (KubernetesHelper.isOpenShift(kubernetes)) {
                manifest = openshiftManifest;
            } else {
                manifest = kubernetesManifest;
            }
            if (!Files.isFile(manifest)) {
                throw new MojoFailureException("No such generated manifest file: " + manifest);
            }

            String namespace = clusterAccess.getNamespace();
            Set<HasMetadata> entities = loadResources(kubernetes, controller, namespace, manifest, project, log);

            String imagePrefix = getImagePrefix(imageName);
            for (HasMetadata entity : entities) {
                updateImageName(kubernetes, namespace, entity, imagePrefix, imageName);
           }
        } catch (KubernetesClientException e) {
            KubernetesResourceUtil.handleKubernetesClientException(e, this.log);
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void updateImageName(KubernetesClient kubernetes, String namespace, HasMetadata entity, String imagePrefix, String imageName) {
        String name = getName(entity);
        if (entity instanceof Deployment) {
            Deployment resource = (Deployment) entity;
            DeploymentSpec spec = resource.getSpec();
            if (spec != null) {
                if (updateImageName(entity, spec.getTemplate(), imagePrefix, imageName)) {
                    kubernetes.extensions().deployments().inNamespace(namespace).withName(name).replace(resource);
                }
            }
        } else if (entity instanceof ReplicaSet) {
            ReplicaSet resource = (ReplicaSet) entity;
            ReplicaSetSpec spec = resource.getSpec();
            if (spec != null) {
                if (updateImageName(entity, spec.getTemplate(), imagePrefix, imageName)) {
                    kubernetes.extensions().replicaSets().inNamespace(namespace).withName(name).replace(resource);
                }
            }
        } else if (entity instanceof ReplicationController) {
            ReplicationController resource = (ReplicationController) entity;
            ReplicationControllerSpec spec = resource.getSpec();
            if (spec != null) {
                if (updateImageName(entity, spec.getTemplate(), imagePrefix, imageName)) {
                    kubernetes.replicationControllers().inNamespace(namespace).withName(name).replace(resource);
                }
            }
        } else if (entity instanceof DeploymentConfig) {
            DeploymentConfig resource = (DeploymentConfig) entity;
            DeploymentConfigSpec spec = resource.getSpec();
            if (spec != null) {
                if (updateImageName(entity, spec.getTemplate(), imagePrefix, imageName)) {
                    OpenShiftClient openshiftClient = new Controller(kubernetes).getOpenShiftClientOrNull();
                    if (openshiftClient == null) {
                        log.warn("Ignoring DeploymentConfig " + name + " as not connected to an OpenShift cluster");
                    }
                    openshiftClient.deploymentConfigs().inNamespace(namespace).withName(name).replace(resource);
                }
            }
        }
    }

    private boolean updateImageName(HasMetadata entity, PodTemplateSpec template, String imagePrefix, String imageName) {
        boolean answer = false;
        PodSpec spec = template.getSpec();
        if (spec != null) {
            List<Container> containers = spec.getContainers();
            if (containers != null) {
                for (Container container : containers) {
                    String image = container.getImage();
                    if (image != null && image.startsWith(imagePrefix)) {
                        container.setImage(imageName);
                        log.info("Updating " + getKind(entity) + " " + getName(entity) + " to use image: " + imageName);
                        answer = true;
                    }
                }
            }
        }
        return answer;
    }

    @Override
    protected String getLogPrefix() {
        return "F8: ";
    }
}
