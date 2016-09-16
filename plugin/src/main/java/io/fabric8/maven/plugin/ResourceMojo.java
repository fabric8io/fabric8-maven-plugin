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

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.extensions.Templates;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.maven.core.access.ClusterAccess;
import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.config.ServiceConfig;
import io.fabric8.maven.core.handler.HandlerHub;
import io.fabric8.maven.core.handler.ReplicationControllerHandler;
import io.fabric8.maven.core.handler.ServiceHandler;
import io.fabric8.maven.core.util.*;
import io.fabric8.maven.docker.AbstractDockerMojo;
import io.fabric8.maven.docker.config.ConfigHelper;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.handler.ImageConfigResolver;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageNameFormatter;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.plugin.converter.DeploymentConfigOpenShiftConverter;
import io.fabric8.maven.plugin.converter.DeploymentOpenShiftConverter;
import io.fabric8.maven.plugin.converter.KubernetesToOpenShiftConverter;
import io.fabric8.maven.plugin.converter.NamespaceOpenShiftConverter;
import io.fabric8.maven.plugin.converter.ReplicSetOpenShiftConverter;
import io.fabric8.maven.plugin.enricher.EnricherManager;
import io.fabric8.maven.plugin.generator.GeneratorManager;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.Template;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenFilteringException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import static io.fabric8.kubernetes.api.KubernetesHelper.getOrCreateAnnotations;


/**
 * Generates or copies the Kubernetes JSON file and attaches it to the build so its
 * installed and released to maven repositories like other build artifacts.
 */
@Mojo(name = "resource", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresDependencyResolution = ResolutionScope.COMPILE)
public class ResourceMojo extends AbstractResourceMojo {

    // THe key how we got the the docker maven plugin
    private static final String DOCKER_MAVEN_PLUGIN_KEY = "io.fabric8:docker-maven-plugin";
    public static final long DEFAULT_OPENSHIFT_DEPLOY_TIMEOUT_SECONDS = 3L * 60 * 60;

    @Component(role = MavenFileFilter.class, hint = "default")
    private MavenFileFilter mavenFileFilter;

    @Component
    private ImageConfigResolver imageConfigResolver;

    // Used for determining which mojos are called during a run
    @Component
    private GoalFinder goalFinder;

    /**
     * Folder where to find project specific files
     */
    @Parameter(property = "fabric8.resourceDir", defaultValue = "${basedir}/src/main/fabric8")
    private File resourceDir;

    /**
     * Should we use the project's compile-time classpath to scan for additional enrichers/generators?
     */
    @Parameter(property = "fabric8.useProjectClasspath", defaultValue = "false")
    private boolean useProjectClasspath = false;

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

    // Resource  specific configuration for this plugin
    @Parameter
    private ResourceConfig resources;

    // Reusing image configuration from d-m-p
    @Parameter
    private List<ImageConfiguration> images;

    /**
     * Whether to perform a Kubernetes build (i.e. against a vanilla Docker daemon) or
     * an OpenShift build (with a Docker build against the OpenShift API server.
     */
    @Parameter(property = "fabric8.mode")
    private PlatformMode mode = PlatformMode.DEFAULT;

    /**
     * OpenShift build mode when an OpenShift build is performed.
     * Can be either "s2i" for an s2i binary build mode or "docker" for a binary
     * docker mode.
     */
    @Parameter(property = "fabric8.build.strategy" )
    private OpenShiftBuildStrategy buildStrategy = OpenShiftBuildStrategy.s2i;

    /**
     * Profile to use. A profile contains the enrichers and generators to
     * use as well as their configuration. Profiles are looked up
     * in the classpath and can be provided as yaml files.
     *
     * However, any given enricher and or generator configuration overrides
     * the information provided by a profile.
     */
    @Parameter(property = "fabric8.profile")
    private String profile;

    /**
     * Enricher specific configuration configuration given through
     * to the various enrichers.
     */
    @Parameter
    private ProcessorConfig enricher;

    /**
     * Configuration passed to generators
     */
    @Parameter
    private ProcessorConfig generator;

    // Whether to use replica sets or replication controller. Could be configurable
    // but for now leave it hidden.
    private boolean useReplicaSet = true;

    // The image configuration after resolving and customization
    private List<ImageConfiguration> resolvedImages;

    // Services
    private HandlerHub handlerHub;

    // Converters for going from Kubernertes objects to openshift objects
    private Map<String, KubernetesToOpenShiftConverter> openShiftConverters;

    /**
     * Namespace to use when accessing Kubernetes or OpenShift
     */
    @Parameter(property = "fabric8.namespace")
    private String namespace;

    /**
     * The OpenShift deploy timeout in seconds:
     * See this issue for background of why for end users on slow wifi on their laptops
     * DeploymentConfigs usually barf: https://github.com/openshift/origin/issues/10531
     */
    @Parameter(property = "fabric8.openshift.deployTimeoutSeconds")
    private Long openshiftDeployTimeoutSeconds;

    // Access for creating OpenShift binary builds
    private ClusterAccess clusterAccess;

    private PlatformMode platformMode;

    public ResourceMojo() {
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        clusterAccess = new ClusterAccess(namespace);
        super.execute();
    }

    public void executeInternal() throws MojoExecutionException, MojoFailureException {
        try {
            platformMode = clusterAccess.resolvePlatformMode(mode, log);

            openShiftConverters = new HashMap<>();
            openShiftConverters.put("ReplicaSet", new ReplicSetOpenShiftConverter());
            openShiftConverters.put("Deployment", new DeploymentOpenShiftConverter(platformMode, getOpenshiftDeployTimeoutSeconds()));
            openShiftConverters.put("DeploymentConfig", new DeploymentConfigOpenShiftConverter(getOpenshiftDeployTimeoutSeconds()));
            openShiftConverters.put("Namespace", new NamespaceOpenShiftConverter());

            handlerHub = new HandlerHub(project);

            // Resolve the Docker image build configuration
            resolvedImages = getResolvedImages(images, log);

            // Manager for calling enrichers.
            EnricherContext ctx = new EnricherContext(project, extractEnricherConfig(), resolvedImages, resources, log, useProjectClasspath);
            EnricherManager enricherManager = new EnricherManager(ctx);

            if (!skip && (!isPomProject() || hasFabric8Dir())) {
                // Generate & write Kubernetes resources
                KubernetesList kubernetesResources = generateKubernetesResources(enricherManager, resolvedImages);

                // Adapt list to use OpenShift specific resource objects
                KubernetesList openShiftResources = convertToOpenShiftResources(kubernetesResources);
                writeResources(openShiftResources, ResourceClassifier.OPENSHIFT);

                filterOpenShiftResources(kubernetesResources);

                // if the list contains a single Template lets unwrap it
                Template template = getSingletonTemplate(openShiftResources);

                if (template != null) {
                    KubernetesList kubernetesTemplateList = createKubernetesTemplate(kubernetesResources, template);
                    if (kubernetesTemplateList != null) {
                        writeResources(kubernetesTemplateList, ResourceClassifier.KUBERNETES_TEMPLATE);
                    }

                    kubernetesResources = replaceTemplateExpressions(kubernetesResources, template);
                }
                writeResources(kubernetesResources, ResourceClassifier.KUBERNETES);

            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate fabric8 descriptor", e);
        }
    }

    private KubernetesList createKubernetesTemplate(KubernetesList kubernetesResources, Template template) {
        Template customTemplate = createTemplateWithObjects(kubernetesResources, template);
        if (customTemplate != null) {
            return new KubernetesListBuilder().withItems(customTemplate).build();
        }
        return null;
    }

    private KubernetesList replaceTemplateExpressions(KubernetesList kubernetesResources, Template template) throws MojoExecutionException {
        Template customTemplate = createTemplateWithObjects(kubernetesResources, template);
        if (customTemplate != null) {
            try {
                return Templates.processTemplatesLocally(customTemplate, false);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to replace template expressions in kubernetes manifest: " + e, e);
            }
        }
        return kubernetesResources;
    }

    private static Template createTemplateWithObjects(KubernetesList kubernetesResources, Template template) {
        List<io.fabric8.openshift.api.model.Parameter> parameters = template.getParameters();
        List<HasMetadata> items = kubernetesResources.getItems();
        Template tempTemplate = null;
        if (parameters != null && parameters.size() > 0 && items != null && items.size() > 0) {
            tempTemplate = new Template();
            tempTemplate.setMetadata(template.getMetadata());
            tempTemplate.setParameters(parameters);
            tempTemplate.setObjects(items);
            defaultAnnotationsFromResources(getOrCreateAnnotations(tempTemplate), items);
        }
        return tempTemplate;
    }

    public Long getOpenshiftDeployTimeoutSeconds() {
        if (openshiftDeployTimeoutSeconds == null) {
            // lets default to a large amount of time which should be enough to download most docker images
            openshiftDeployTimeoutSeconds = DEFAULT_OPENSHIFT_DEPLOY_TIMEOUT_SECONDS;
        }
        return openshiftDeployTimeoutSeconds;
    }

    public void setOpenshiftDeployTimeoutSeconds(Long openshiftDeployTimeoutSeconds) {
        this.openshiftDeployTimeoutSeconds = openshiftDeployTimeoutSeconds;
    }

    private void filterOpenShiftResources(KubernetesList list) {
        List<HasMetadata> items = list.getItems();
        boolean changed = false;
        for (HasMetadata item : new ArrayList<>(items)) {
            String name = item.getClass().getPackage().getName();
            if (name.contains("openshift")) {
                if (items.remove(item)) {
                    changed = true;
                    getLog().info("Filtered " + KubernetesHelper.getKind(item) + " " + KubernetesHelper.getName(item) + " from the kubernetes yaml");
                }
            }
        }
        if (changed) {
            list.setItems(items);
        }
    }

    private KubernetesList generateKubernetesResources(final EnricherManager enricherManager, List<ImageConfiguration> images)
        throws IOException, MojoExecutionException {
        File[] resourceFiles = KubernetesResourceUtil.listResourceFragments(resourceDir);
        KubernetesListBuilder builderApp, builderSupport;

        // Add resource files found in the fabric8 directory
        if (resourceFiles != null && resourceFiles.length > 0) {
            log.info("Using resource templates from %s", resourceDir);
            String defaultName = MavenUtil.createDefaultResourceName(project);
            builderApp = KubernetesResourceUtil.readResourceFragmentsFrom(
                KubernetesResourceUtil.API_VERSION,
                KubernetesResourceUtil.API_EXTENSIONS_VERSION,
                defaultName, true,
                filterFiles(resourceFiles));
            builderSupport = KubernetesResourceUtil.readResourceFragmentsFrom(
                KubernetesResourceUtil.API_VERSION,
                KubernetesResourceUtil.API_EXTENSIONS_VERSION,
                defaultName, false,
                filterFiles(resourceFiles));
        } else {
            builderApp = new KubernetesListBuilder();
            builderSupport = new KubernetesListBuilder();
        }

        // Add locally configured objects
        if (resources != null) {
            // TODO: Allow also support resources to be specified via XML
            addConfiguredResources(builderApp, builderSupport, images);
        }

        // Create default resources for app resources only
        enricherManager.createDefaultResources(builderApp);

        // ----------------------------------------------
        // Rest is done for all resources:
        KubernetesListBuilder builderCombined = new KubernetesListBuilder();
        builderCombined.withItems(builderApp.getItems());
        builderCombined.addToItems(
            new ArrayList<HasMetadata>(builderSupport.getItems()).toArray(new HasMetadata[0]));

        // Enrich descriptors
        enricherManager.enrich(builderCombined);

        return builderCombined.build();
    }

    private ProcessorConfig extractEnricherConfig() throws IOException {
        return ProfileUtil.extractProcesssorConfiguration(enricher,ProfileUtil.ENRICHER_CONFIG, profile, resourceDir);
    }

    private ProcessorConfig extractGeneratorConfig() throws IOException {
        return ProfileUtil.extractProcesssorConfiguration(generator, ProfileUtil.GENERATOR_CONFIG, profile, resourceDir);
    }


    // Converts the kubernetes resources into OpenShift resources
    private KubernetesList convertToOpenShiftResources(KubernetesList resources) {
        KubernetesListBuilder builder = new KubernetesListBuilder();
        builder.withMetadata(resources.getMetadata());
        List<HasMetadata> items = resources.getItems();
        List<HasMetadata> objects = new ArrayList<>();
        if (items != null) {
            for (HasMetadata item : items) {
                if (item instanceof Deployment) {
                    // if we have a Deployment and a DeploymentConfig of the same name
                    // since we have a different manifest for OpenShift then lets filter out
                    // the Kubernetes specific Deployment
                    String name = KubernetesHelper.getName(item);
                    if (hasDeploymentConfigNamed(items, name)) {
                        continue;
                    }
                }
                HasMetadata converted = convertKubernetesItemToOpenShift(item);
                if (converted != null) {
                    objects.add(converted);
                }
            }
        }
        Template template = removeFirstTemplate(objects);
        if (template != null) {
            template.setObjects(objects);
            defaultAnnotationsFromResources(getOrCreateAnnotations(template), objects);
            builder.addToItems(template);
        } else {
            for (HasMetadata object : objects) {
                builder.addToItems(object);
            }
        }
        return builder.build();
    }

    private Template removeFirstTemplate(List<HasMetadata> items) {
        Template firstTemplate = null;
        for (HasMetadata item : new ArrayList<>(items)) {
            if (item instanceof Template) {
                Template template = (Template) item;
                if (firstTemplate == null) {
                    firstTemplate = template;
                } else {
                    firstTemplate = Templates.combineTemplates(firstTemplate, template);
                }
                items.remove(item);
            }
        }
        return firstTemplate;
    }

    private boolean hasDeploymentConfigNamed(List<HasMetadata> items, String name) {
        for (HasMetadata item : items) {
            if (item instanceof DeploymentConfig) {
                String dcName = KubernetesHelper.getName(item);
                if (Objects.equals(name, dcName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Converts any kubernetes resource to the OpenShift equivalent
     *
     * @return the converted kubernetes resource or null if it should be ignored
     */
    private HasMetadata convertKubernetesItemToOpenShift(HasMetadata item) {
        KubernetesToOpenShiftConverter converter = openShiftConverters.get(item.getKind());
        return converter != null ? converter.convert(item) : item;
    }

    // ==================================================================================


    private List<ImageConfiguration> getResolvedImages(List<ImageConfiguration> images, final Logger log) throws MojoExecutionException {
        List<ImageConfiguration> ret;
        final Properties resolveProperties = project.getProperties();
        ret = ConfigHelper.resolveImages(
            log,
            images,
            new ConfigHelper.Resolver() {
                @Override
                public List<ImageConfiguration> resolve(ImageConfiguration image) {
                    return imageConfigResolver.resolve(image, resolveProperties);
                }
            },
            null,  // no filter on image name yet (TODO: Maybe add this, too ?)
            new ConfigHelper.Customizer() {
                @Override
                public List<ImageConfiguration> customizeConfig(List<ImageConfiguration> configs) {
                    try {
                        return GeneratorManager.generate(configs, extractGeneratorConfig(), project, log, mode, buildStrategy, useProjectClasspath);
                    } catch (IOException e) {
                        throw new IllegalArgumentException("Cannot extract generator: " + e,e);
                    }
                }
            });

        Date now = getBuildReferenceDate();
        storeReferenceDateInPluginContext(now);
        String minimalApiVersion = ConfigHelper.initAndValidate(ret, null /* no minimal api version */,
                                                                new ImageNameFormatter(project,now), log);
        return ret;
    }

    private void storeReferenceDateInPluginContext(Date now) {
        Map<String, Object> pluginContext = getPluginContext();
        pluginContext.put(AbstractDockerMojo.CONTEXT_KEY_BUILD_TIMESTAMP, now);
    }


    // get a reference date
    private Date getBuildReferenceDate() throws MojoExecutionException {
        if (goalFinder.runningWithGoal(project, session, "fabric8:build") ||
                goalFinder.runningWithGoal(project, session, "fabric8:deploy") ||
                goalFinder.runningWithGoal(project, session, "fabric8:run")) {
            // we are running together with fabric8:build, but since fabric8:build is running later we
            // are creating the build date here which is reused by fabric8:build
            return new Date();
        } else {
            // Pick up an existing build date created by fabric8:build previously
            File tsFile = new File(project.getBuild().getDirectory(),AbstractDockerMojo.DOCKER_BUILD_TIMESTAMP);
            if (!tsFile.exists()) {
                return new Date();
            }
            try {
                return EnvUtil.loadTimestamp(tsFile);
            } catch (MojoExecutionException e) {
                throw new MojoExecutionException("Cannot read timestamp from " + tsFile,e);
            }
        }
    }

    private void addConfiguredResources(KubernetesListBuilder builder, KubernetesListBuilder builderSupport, List<ImageConfiguration> images) {

        log.info("Adding resources from plugin configuration");
        addServices(builder, resources.getServices());
        addController(builder, images);
    }

    private void addController(KubernetesListBuilder builder, List<ImageConfiguration> images) {
        // TODO: Change to ReplicaSet
        ReplicationControllerHandler rcHandler = handlerHub.getReplicationControllerHandler();
        if (resources.getReplicaSetName() != null) {
            builder.addToReplicationControllerItems(rcHandler.getReplicationController(resources, images));
        }
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

    private void addServices(KubernetesListBuilder builder, List<ServiceConfig> serviceConfig) {
        if (serviceConfig != null) {
            ServiceHandler serviceHandler = handlerHub.getServiceHandler();
            builder.addToServiceItems(toArray(serviceHandler.getServices(serviceConfig)));
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
