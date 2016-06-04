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
package io.fabric8.maven.plugin;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.extensions.*;
import io.fabric8.maven.core.config.ResourceConfiguration;
import io.fabric8.maven.core.config.ServiceConfiguration;
import io.fabric8.maven.core.handler.HandlerHub;
import io.fabric8.maven.core.handler.ReplicationControllerHandler;
import io.fabric8.maven.core.handler.ServiceHandler;
import io.fabric8.maven.core.util.*;
import io.fabric8.maven.docker.config.ConfigHelper;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.handler.ImageConfigResolver;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.plugin.customizer.CustomizerManager;
import io.fabric8.maven.plugin.enricher.EnricherManager;
import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import io.fabric8.openshift.api.model.DeploymentConfigFluent;
import io.fabric8.utils.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenFilteringException;

import static io.fabric8.maven.core.util.MavenProperties.DOCKER_IMAGE_LABEL;
import static io.fabric8.maven.core.util.MavenProperties.DOCKER_IMAGE_NAME;
import static io.fabric8.maven.core.util.MavenProperties.DOCKER_IMAGE_USER;
import static io.fabric8.maven.core.util.ResourceFileType.yaml;


/**
 * Generates or copies the Kubernetes JSON file and attaches it to the build so its
 * installed and released to maven repositories like other build artifacts.
 */
@Mojo(name = "resource", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresDependencyResolution = ResolutionScope.COMPILE, requiresDependencyCollection = ResolutionScope.COMPILE)
public class ResourceMojo extends AbstractFabric8Mojo {

    @Component(role = MavenFileFilter.class, hint = "default")
    private MavenFileFilter mavenFileFilter;

    @Component
    private ImageConfigResolver imageConfigResolver;

    /**
     * Folder where to find project specific files
     */
    @Parameter(property = "fabric8.resourceDir", defaultValue = "${basedir}/src/main/fabric8")
    private File resourceDir;

    /**
     * The artifact type for attaching the generated resource file to the project.
     * Can be either 'json' or 'yaml'
     */
    @Parameter(property = "fabric8.resourceType")
    private ResourceFileType resourceFileType = yaml;

    /**
     * The generated kubernetes JSON file
     */
    @Parameter(property = "fabric8.targetDir", defaultValue = "${project.build.outputDirectory}/META-INF/fabric8")
    private File targetDir;

    /**
     * The fabric8 working directory
     */
    @Parameter(property = "fabric8.workDir", defaultValue = "${project.build.directory}/fabric8")
    private File workDir;

    /**
     * Whether to skip the execution of this plugin. Best used as property "fabric8.skip"
     */
    @Parameter(property = "fabric8.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Whether to use a timestamped label when building SNAPSHOT versions
     */
    @Parameter(defaultValue = "true")
    private boolean snapshotLabelUseTimestamp;

    /**
     * Whether to use the docker label of <code>latest</code> when building SNAPSHOT versions
     */
    @Parameter(defaultValue = "true")
    private boolean snapshotLabelUseLatest;


    // Resource  specific configuration for this plugin
    @Parameter
    private ResourceConfiguration resources;

    // Reusing image configuration from d-m-p
    @Parameter
    private List<ImageConfiguration> images;

    /**
     * Enricher specific configuration configuration given through
     * to the various enrichers.
     */
    @Parameter
    private Map<String, String> enricher;

    /**
     * Configuration passed to customizers
     */
    @Parameter
    private Map<String, String> customizer;

    @Component
    private MavenProjectHelper projectHelper;


    /**
     * The artifact type for attaching the generated kubernetes YAML file to the project
     */
    @Parameter(property = "fabric8.artifactType", defaultValue = "yml")
    private String artifactType;

    // Whether to use replica sets or replication controller. Could be configurable
    // but for now leave it hidden.
    private boolean useReplicaSet = true;

    // The image configuration after resolving and customization
    private List<ImageConfiguration> resolvedImages;

    // Services
    private HandlerHub handlerHub;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            defineCustomProperties(project);
            handlerHub = new HandlerHub(project);

            // Resolve the Docker image build configuration
            resolvedImages = resolveImages(images, log);

            // Manager for calling enrichers.
            EnricherContext ctx = new EnricherContext(project, enricher, resolvedImages, resources, log);
            EnricherManager enricherManager = new EnricherManager(ctx);

            if (!skip && (!isPomProject() || hasFabric8Dir())) {
                // Generate & write Kubernetes resources
                KubernetesList kubernetesResources = generateKubernetesResources(enricherManager, resolvedImages);
                writeResources(kubernetesResources, ResourceClassifier.KUBERNETES);

                // Adapt list to use OpenShift specific resource objects
                KubernetesList openShiftResources = convertToOpenShiftResources(kubernetesResources);
                writeResources(openShiftResources, ResourceClassifier.OPENSHIFT);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate fabric8 descriptor", e);
        }
    }

    private void writeResources(KubernetesList resources, ResourceClassifier classifier) throws IOException {
        // write kubernetes.yml / openshift.yml
        File resourceFileBase = new File(this.targetDir, classifier.getValue());
        File file = KubernetesResourceUtil.writeResource(resources, resourceFileBase, resourceFileType);

        // Attach it to the Maven reactor so that it will also get deployed
        projectHelper.attachArtifact(project, artifactType, classifier.getValue(), file);

        // write separate files, one for each resource item
        writeIndividualResources(resources, resourceFileBase);
    }


    private void writeIndividualResources(KubernetesList resources, File targetDir) throws IOException {
        for (HasMetadata item : resources.getItems()) {
            String name = KubernetesHelper.getName(item);
            if (Strings.isNullOrBlank(name)) {
                log.error("No name for generated item " + item);
                continue;
            }
            String itemFile = KubernetesResourceUtil.getNameWithSuffix(name, item.getKind());
            File itemTarget = new File(targetDir, itemFile);
            KubernetesResourceUtil.writeResource(item, itemTarget, resourceFileType);
        }
    }


    // Converts the kubernetes resources into OpenShift resources
    private KubernetesList convertToOpenShiftResources(KubernetesList resources) {
        KubernetesListBuilder builder = new KubernetesListBuilder();
        builder.withMetadata(resources.getMetadata());
        List<HasMetadata> items = resources.getItems();
        if (items != null) {
            for (HasMetadata item : items) {
                HasMetadata openshiftItem = convertKubernetesItemToOpenShift(item);
                if (openshiftItem != null) {
                    builder.addToItems(openshiftItem);
                }
            }
        }
        return builder.build();
    }

    /**
     * Converts any kubernetes resource to the OpenShift equivalent
     *
     * @return the converted kubernetes resource or null if it should be ignored
     */
    private HasMetadata convertKubernetesItemToOpenShift(HasMetadata item) {
        if (item instanceof ReplicaSet) {
            ReplicaSet resource = (ReplicaSet) item;
            ReplicationControllerBuilder builder = new ReplicationControllerBuilder();
            builder.withMetadata(resource.getMetadata());
            ReplicaSetSpec spec = resource.getSpec();
            if (spec != null) {
                ReplicationControllerFluent.SpecNested<ReplicationControllerBuilder> specBuilder = builder.withNewSpec();
                Integer replicas = spec.getReplicas();
                if (replicas != null) {
                    specBuilder.withReplicas(replicas);
                }
                LabelSelector selector = spec.getSelector();
                if (selector  != null) {
                    Map<String, String> matchLabels = selector.getMatchLabels();
                    if (matchLabels != null && !matchLabels.isEmpty()) {
                        specBuilder.withSelector(matchLabels);
                    }
                }
                PodTemplateSpec template = spec.getTemplate();
                if (template != null) {
                    specBuilder.withTemplate(template);
                }
                specBuilder.endSpec();
            }
            return builder.build();
        } else if (item instanceof Deployment) {
            Deployment resource = (Deployment) item;
            DeploymentConfigBuilder builder = new DeploymentConfigBuilder();
            builder.withMetadata(resource.getMetadata());
            DeploymentSpec spec = resource.getSpec();
            if (spec != null) {
                DeploymentConfigFluent.SpecNested<DeploymentConfigBuilder> specBuilder = builder.withNewSpec();
                Integer replicas = spec.getReplicas();
                if (replicas != null) {
                    specBuilder.withReplicas(replicas);
                }
                LabelSelector selector = spec.getSelector();
                if (selector  != null) {
                    Map<String, String> matchLabels = selector.getMatchLabels();
                    if (matchLabels != null && !matchLabels.isEmpty()) {
                        specBuilder.withSelector(matchLabels);
                    }
                }
                PodTemplateSpec template = spec.getTemplate();
                if (template != null) {
                    specBuilder.withTemplate(template);
                }
                DeploymentStrategy strategy = spec.getStrategy();
                if (strategy != null) {
                    // TODO is there any values we can copy across?
                    //specBuilder.withStrategy(strategy);
                }

                // lets add a default trigger so that its triggered when we change its config
                specBuilder.addNewTrigger().withType("ConfigChange").endTrigger();

                specBuilder.endSpec();
            }
            return builder.build();

        }
        return item;
    }

    private void defineCustomProperties(MavenProject project) {
        Properties properties = project.getProperties();
        String label = properties.getProperty(DOCKER_IMAGE_LABEL);
        if (Strings.isNullOrBlank(label)) {
            label = project.getVersion();
            if (label.endsWith("-SNAPSHOT")) {
                if (snapshotLabelUseTimestamp) {
                    label = "snapshot-" + new SimpleDateFormat("yyMMdd-HHmmss-SSSS").format(new Date());

                } else if (snapshotLabelUseLatest) {
                    label = "latest";
                }
            }
            properties.setProperty(DOCKER_IMAGE_LABEL, label);
        }
        if (Strings.isNullOrBlank(properties.getProperty(DOCKER_IMAGE_NAME))) {
            properties.setProperty(DOCKER_IMAGE_NAME, DockerUtil.prepareImageNamePart(project));
        }
        if (Strings.isNullOrBlank(properties.getProperty(DOCKER_IMAGE_USER))) {
            properties.setProperty(DOCKER_IMAGE_USER, DockerUtil.prepareUserName(project));
        }

        System.out.println("=> " + DOCKER_IMAGE_USER + " = " + properties.getProperty(DOCKER_IMAGE_USER));
        System.out.println("=> " + DOCKER_IMAGE_NAME + " = " + properties.getProperty(DOCKER_IMAGE_NAME));
        System.out.println("=> " + DOCKER_IMAGE_LABEL + " = " + properties.getProperty(MavenProperties.DOCKER_IMAGE_LABEL));
    }

    private List<ImageConfiguration> resolveImages(List<ImageConfiguration> images, Logger log) {
        final Properties resolveProperties = project.getProperties();
        List<ImageConfiguration> ret = ConfigHelper.resolveImages(
            images,
            new ConfigHelper.Resolver() {
                @Override
                public List<ImageConfiguration> resolve(ImageConfiguration image) {
                    return imageConfigResolver.resolve(image, resolveProperties);
                }
            },
            null,  // no filter
            new ConfigHelper.Customizer() {
                @Override
                public List<ImageConfiguration> customizeConfig(List<ImageConfiguration> configs) {
                    return CustomizerManager.customize(configs, customizer, project);
                }
            });

        ConfigHelper.initAndValidate(ret, null, log);
        return ret;
    }

    // ==================================================================================

    private KubernetesList generateKubernetesResources(final EnricherManager enricherManager, List<ImageConfiguration> images)
        throws IOException, MojoExecutionException {
        File[] resourceFiles = KubernetesResourceUtil.listResourceFragments(resourceDir);
        ReplicationControllerHandler rcHandler = handlerHub.getReplicationControllerHandler();

        KubernetesListBuilder builder;

        // Add resource files found in the fabric8 directory
        if (resourceFiles != null && resourceFiles.length > 0) {
            log.info("Using resource templates from %s", resourceDir);
            builder = KubernetesResourceUtil.readResourceFragmentsFrom(KubernetesResourceUtil.API_VERSION, KubernetesResourceUtil.API_EXTENSIONS_VERSION, filterFiles(resourceFiles));
        } else {
            builder = new KubernetesListBuilder();
        }

        // Add services + replicaSet if configured in plugin config
        if (resources != null) {
            log.info("Adding resources from plugin configuration");
            addServices(builder, resources.getServices(), resources.getAnnotations().getService());
            // TODO: Change to ReplicaSet ...
            builder.addToReplicationControllerItems(rcHandler.getReplicationController(resources, images));
        }

        // Add default resources
        enricherManager.addDefaultResources(builder);

        // Enrich labels
        enricherManager.enrichLabels(builder);

        // Add missing selectors
        enricherManager.addMissingSelectors(builder);

        // Final customization step
        enricherManager.adapt(builder);

        return builder.build();
    }

    private File[] filterFiles(File[] resourceFiles) throws MojoExecutionException {
        if (!workDir.exists()) {
            if (!workDir.mkdirs()) {
                throw new MojoExecutionException("Cannot create working dir " + workDir);
            }
        }
        File[] ret = new File[resourceFiles.length];
        int i = 0;
        for (File resource : resourceFiles) {
            File targetFile = new File(workDir, resource.getName());
            try {
                mavenFileFilter.copyFile(resource, targetFile, true,
                                         project, null, false, "utf8", session);
                ret[i++] = targetFile;
            } catch (MavenFilteringException exp) {
                throw new MojoExecutionException(
                    String.format("Cannot filter %s to %s", resource, targetFile), exp);
            }
        }
        return ret;
    }

    private void addServices(KubernetesListBuilder builder, List<ServiceConfiguration> serviceConfig, Map<String, String> annotations) {
        if (serviceConfig != null) {
            ServiceHandler serviceHandler = handlerHub.getServiceHandler();
            builder.addToServiceItems(toArray(serviceHandler.getServices(serviceConfig, annotations)));
        }
    }

    // convert list to array, never returns null.
    private Service[] toArray(List<Service> services) {
        if (services == null) {
            return new Service[0];
        }
        if (services instanceof ArrayList) {
            return (Service[]) ((ArrayList) services).toArray(new Service[services.size()]);
        } else {
            Service[] ret = new Service[services.size()];
            for (int i = 0; i < services.size(); i++) {
                ret[i] = services.get(i);
            }
            return ret;
        }
    }


    private boolean hasFabric8Dir() {
        return resourceDir.isDirectory();
    }

    private boolean isPomProject() {
        return "pom".equals(project.getPackaging());
    }

}
