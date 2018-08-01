package io.fabric8.maven.watcher.standard;

import java.util.Date;
import java.util.List;
import java.util.Set;

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
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.util.kubernetes.KubernetesHelper;
import io.fabric8.maven.core.util.kubernetes.KubernetesResourceUtil;
import io.fabric8.maven.core.util.kubernetes.OpenshiftHelper;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.BuildService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.service.WatchService;
import io.fabric8.maven.docker.util.ImageNameFormatter;
import io.fabric8.maven.docker.util.Task;
import io.fabric8.maven.watcher.api.BaseWatcher;
import io.fabric8.maven.watcher.api.WatcherContext;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigSpec;
import io.fabric8.openshift.client.OpenShiftClient;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 *
 */
public class DockerImageWatcher extends BaseWatcher {

    public DockerImageWatcher(WatcherContext watcherContext) {
        super(watcherContext, "docker-image");
    }

    @Override
    public boolean isApplicable(List<ImageConfiguration> configs, Set<HasMetadata> resources, PlatformMode mode) {
        return mode == PlatformMode.kubernetes;
    }

    @Override
    public void watch(List<ImageConfiguration> configs, final Set<HasMetadata> resources, PlatformMode mode) {

        BuildService.BuildContext buildContext = getContext().getBuildContext();
        WatchService.WatchContext watchContext = getContext().getWatchContext();

        // add a image customizer
        watchContext = new WatchService.WatchContext.Builder(watchContext)
                .imageCustomizer(new Task<ImageConfiguration>() {
                    @Override
                    public void execute(ImageConfiguration imageConfiguration) throws DockerAccessException, MojoExecutionException, MojoFailureException {
                        buildImage(imageConfiguration);
                    }
                }).containerRestarter(new Task<WatchService.ImageWatcher>() {
                    @Override
                    public void execute(WatchService.ImageWatcher imageWatcher) throws DockerAccessException, MojoExecutionException, MojoFailureException {
                        restartContainer(imageWatcher, resources);
                    }
                })
                .build();

        ServiceHub hub = getContext().getServiceHub();
        try {
            hub.getWatchService().watch(watchContext, buildContext, configs);
        } catch (Exception ex) {
            throw new RuntimeException("Error while watching", ex);
        }
    }

    protected void buildImage(ImageConfiguration imageConfig) throws DockerAccessException, MojoExecutionException {
        String imageName = imageConfig.getName();
        // lets regenerate the label
        try {
            String imagePrefix = getImagePrefix(imageName);
            imageName = imagePrefix + "%t";
            ImageNameFormatter formatter = new ImageNameFormatter(getContext().getProject(), new Date());
            imageName = formatter.format(imageName);
            imageConfig.setName(imageName);
            log.info("New image name: " + imageConfig.getName());
        } catch (Exception e) {
            log.error("Caught: " + e, e);
        }

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

    protected void restartContainer(WatchService.ImageWatcher watcher, Set<HasMetadata> resources) throws MojoExecutionException {
        ImageConfiguration imageConfig = watcher.getImageConfiguration();
        String imageName = imageConfig.getName();
        try {
            ClusterAccess clusterAccess = new ClusterAccess(getContext().getNamespace());
            KubernetesClient client = clusterAccess.createDefaultClient(log);

            String namespace = clusterAccess.getNamespace();

            String imagePrefix = getImagePrefix(imageName);
            for (HasMetadata entity : resources) {
                updateImageName(client, namespace, entity, imagePrefix, imageName);
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
        String name = KubernetesHelper.getName(entity);
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
                    OpenShiftClient openshiftClient = OpenshiftHelper.asOpenShiftClient(kubernetes);
                    if (openshiftClient == null) {
                        log.warn("Ignoring DeploymentConfig %s as not connected to an OpenShift cluster", name);
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
                        log.info("Updating " + KubernetesHelper.getKind(entity) + " " + KubernetesHelper.getName(entity) + " to use image: " + imageName);
                        answer = true;
                    }
                }
            }
        }
        return answer;
    }
}
