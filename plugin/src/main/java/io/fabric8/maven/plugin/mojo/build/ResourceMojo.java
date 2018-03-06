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
package io.fabric8.maven.plugin.mojo.build;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.extensions.Templates;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.maven.core.access.ClusterAccess;
import io.fabric8.maven.core.config.*;
import io.fabric8.maven.core.handler.HandlerHub;
import io.fabric8.maven.core.handler.ReplicationControllerHandler;
import io.fabric8.maven.core.handler.ServiceHandler;
import io.fabric8.maven.core.service.ComposeService;
import io.fabric8.maven.core.service.Fabric8ServiceException;
import io.fabric8.maven.core.util.*;
import io.fabric8.maven.core.util.validator.ResourceValidator;
import io.fabric8.maven.docker.AbstractDockerMojo;
import io.fabric8.maven.docker.config.ConfigHelper;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.handler.ImageConfigResolver;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageNameFormatter;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.util.InitContainerHandler;
import io.fabric8.maven.enricher.standard.VolumePermissionEnricher;
import io.fabric8.maven.generator.api.GeneratorContext;
import io.fabric8.maven.plugin.converter.DeploymentConfigOpenShiftConverter;
import io.fabric8.maven.plugin.converter.DeploymentOpenShiftConverter;
import io.fabric8.maven.plugin.converter.KubernetesToOpenShiftConverter;
import io.fabric8.maven.plugin.converter.NamespaceOpenShiftConverter;
import io.fabric8.maven.plugin.converter.ReplicSetOpenShiftConverter;
import io.fabric8.maven.plugin.enricher.EnricherManager;
import io.fabric8.maven.plugin.generator.GeneratorManager;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.Template;
import io.fabric8.utils.Lists;
import io.fabric8.utils.Strings;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenFilteringException;

import javax.validation.ConstraintViolationException;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import static io.fabric8.maven.core.util.Constants.RESOURCE_APP_CATALOG_ANNOTATION;
import static io.fabric8.maven.plugin.mojo.build.ApplyMojo.DEFAULT_OPENSHIFT_MANIFEST;


/**
 * Generates or copies the Kubernetes JSON file and attaches it to the build so its
 * installed and released to maven repositories like other build artifacts.
 */
@Mojo(name = "resource", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresDependencyResolution = ResolutionScope.COMPILE)
public class ResourceMojo extends AbstractResourceMojo {
    /**
     * Used to annotate a resource as being for a specific platform only such as "kubernetes" or "openshift"
     */
    public static final String TARGET_PLATFORM_ANNOTATION = "fabric8.io/target-platform";

    // THe key how we got the the docker maven plugin
    private static final String DOCKER_MAVEN_PLUGIN_KEY = "io.fabric8:docker-maven-plugin";
    private static final String DOCKER_IMAGE_USER = "docker.image.user";

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
     * Folder where to find project specific files
     */
    @Parameter(property = "fabric8.resourceDirOpenShiftOverride", defaultValue = "${basedir}/src/main/fabric8-openshift-override")
    private File resourceDirOpenShiftOverride;

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
     * The fabric8 working directory
     */
    @Parameter(property = "fabric8.workDirOpenShiftOverride", defaultValue = "${project.build.directory}/fabric8-openshift-override")
    private File workDirOpenShiftOverride;

    /**
     * Directory to lookup for docker compose files
     */
    @Parameter(property = "compose.resourceDir", defaultValue = "${basedir}/src/main/fabric8-compose")
    private File composeResourceDir;

    // Docker compose resource specific configuration for this plugin
    @Parameter
    private String composeFile;

    // Resource specific configuration for this plugin
    @Parameter
    private ResourceConfig resources;

    // Skip resource descriptors validation
    @Parameter(property = "fabric8.skipResourceValidation", defaultValue = "false")
    private Boolean skipResourceValidation;

    // Determine if the plugin should stop when a validation error is encountered
    @Parameter(property = "fabric8.failOnValidationError", defaultValue = "false")
    private Boolean failOnValidationError;

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
    @Parameter(property = "fabric8.build.strategy")
    private OpenShiftBuildStrategy buildStrategy = OpenShiftBuildStrategy.s2i;

    @Parameter(property = "fabric8.build.switchToDeployment", defaultValue = "false")
    private Boolean switchToDeployment;
    /**
     * Profile to use. A profile contains the enrichers and generators to
     * use as well as their configuration. Profiles are looked up
     * in the classpath and can be provided as yaml files.
     * <p>
     * However, any given enricher and or generator configuration overrides
     * the information provided by a profile.
     */
    @Parameter(property = "fabric8.profile")
    private String profile;

    /**
     * The generated openshift YAML file
     */
    @Parameter(property = "fabric8.openshiftManifest", defaultValue = DEFAULT_OPENSHIFT_MANIFEST)
    private File openshiftManifest;

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
     *
     * Please follow also the discussion at
     * <ul>
     *     <li>https://github.com/fabric8io/fabric8-maven-plugin/pull/944#discussion_r116962969</li>
     *     <li>https://github.com/fabric8io/fabric8-maven-plugin/pull/794</li>
     * </ul>
     * and the references within it for the reason of this ridiculous long default timeout
     * (in short: Its because Docker image download times are added to the deployment time, making
     * the default of 10 minutes quite unusable if multiple images are included in the deployment).
     */
    @Parameter(property = "fabric8.openshift.deployTimeoutSeconds", defaultValue = "3600")
    private Long openshiftDeployTimeoutSeconds;

    /**
     * If set to true it would set the container image reference to "", this is done to handle weird
     * behavior of Openshift 3.7 in which subsequent rollouts lead to ImagePullErr
     *
     * Please see discussion at
     * <ul>
     *     <li>https://github.com/openshift/origin/issues/18406</li>
     *     <li>https://github.com/fabric8io/fabric8-maven-plugin/issues/1130</li>
     * </ul>
     */
    @Parameter(property = "fabric8.openshift.trimImageInContainerSpec", defaultValue = "false")
    private Boolean trimImageInContainerSpec;

    @Parameter(property = "fabric8.openshift.generateRoute", defaultValue = "true")
    private Boolean generateRoute;

    @Parameter(property = "fabric8.openshift.enableAutomaticTrigger", defaultValue = "true")
    private Boolean enableAutomaticTrigger;

    @Parameter(property = "kompose.dir", defaultValue = "${user.home}/.kompose/bin")
    private File komposeBinDir;

    // Access for creating OpenShift binary builds
    private ClusterAccess clusterAccess;

    private PlatformMode platformMode;

    private OpenShiftDependencyResources openshiftDependencyResources;
    private OpenShiftOverrideResources openShiftOverrideResources;

    public void executeInternal() throws MojoExecutionException, MojoFailureException {
        clusterAccess = new ClusterAccess(namespace);

        try {
            lateInit();

            // Resolve the Docker image build configuration
            resolvedImages = getResolvedImages(images, log);

            if (!skip && (!isPomProject() || hasFabric8Dir())) {
                // Extract and generate resources which can be a mix of Kubernetes and OpenShift resources
                KubernetesList resources = generateResources(resolvedImages);

                // Adapt list to use OpenShift specific resource objects
                KubernetesList openShiftResources = convertToOpenShiftResources(resources);
                writeResources(openShiftResources, ResourceClassifier.OPENSHIFT, generateRoute);
                File openShiftResourceDir = new File(this.targetDir, ResourceClassifier.OPENSHIFT.getValue());
                validateIfRequired(openShiftResourceDir, ResourceClassifier.OPENSHIFT);

                // Remove OpenShift specific stuff provided by fragments
                KubernetesList kubernetesResources = convertToKubernetesResources(resources, openShiftResources);
                writeResources(kubernetesResources, ResourceClassifier.KUBERNETES, generateRoute);
                File kubernetesResourceDir = new File(this.targetDir, ResourceClassifier.KUBERNETES.getValue());
                validateIfRequired(kubernetesResourceDir, ResourceClassifier.KUBERNETES);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate fabric8 descriptor", e);
        }
    }

    private void validateIfRequired(File resourceDir, ResourceClassifier classifier) throws MojoExecutionException, MojoFailureException {
        try {
            if (!skipResourceValidation) {
                new ResourceValidator(resourceDir, classifier, log).validate();
            }
        } catch (ConstraintViolationException e) {
            if (failOnValidationError) {
                log.error("[[R]]" + e.getMessage() + "[[R]]");
                log.error("[[R]]use \"mvn -Dfabric8.skipResourceValidation=true\" option to skip the validation[[R]]");
                throw new MojoFailureException("Failed to generate fabric8 descriptor");
            } else {
                log.warn("[[Y]]" + e.getMessage() + "[[Y]]");
            }
        } catch (Throwable e) {
            if (failOnValidationError) {
                throw new MojoExecutionException("Failed to validate resources", e);
            } else {
                log.warn("Failed to validate resources: %s", e.getMessage());
            }
        }
    }

    private void lateInit() throws MojoExecutionException {
        if (goalFinder.runningWithGoal(project, session, "fabric8:watch") ||
                goalFinder.runningWithGoal(project, session, "fabric8:watch")) {
            Properties properties = project.getProperties();
            properties.setProperty("fabric8.watch", "true");
        }
        platformMode = clusterAccess.resolvePlatformMode(mode, log);
        log.info("Running in [[B]]%s[[B]] mode", platformMode.getLabel());

        if (isOpenShiftMode()) {
            Properties properties = project.getProperties();
            if (!properties.contains(DOCKER_IMAGE_USER)) {
                String namespace = clusterAccess.getNamespace();
                log.info("Using docker image name of namespace: " + namespace);
                properties.setProperty(DOCKER_IMAGE_USER, namespace);
            }
            if (!properties.contains(PlatformMode.FABRIC8_EFFECTIVE_PLATFORM_MODE)) {
                properties.setProperty(PlatformMode.FABRIC8_EFFECTIVE_PLATFORM_MODE, platformMode.toString());
            }
        }

        openShiftConverters = new HashMap<>();
        openShiftConverters.put("ReplicaSet", new ReplicSetOpenShiftConverter());
        openShiftConverters.put("Deployment", new DeploymentOpenShiftConverter(platformMode, getOpenshiftDeployTimeoutSeconds()));
        // TODO : This converter shouldn't be here. See its javadoc.
        openShiftConverters.put("DeploymentConfig", new DeploymentConfigOpenShiftConverter(getOpenshiftDeployTimeoutSeconds()));
        openShiftConverters.put("Namespace", new NamespaceOpenShiftConverter());

        handlerHub = new HandlerHub(project);
    }

    private boolean isOpenShiftMode() {
        return platformMode.equals(PlatformMode.openshift);
    }

    private KubernetesList convertToKubernetesResources(KubernetesList resources, KubernetesList openShiftResources) throws MojoExecutionException {
        KubernetesList kubernetesResources = removeOpenShiftObjects(resources);
        KubernetesList kubernetesList = processOpenshiftTemplateIfProvided(openShiftResources, kubernetesResources);
        return kubernetesList;
    }

    private KubernetesList processOpenshiftTemplateIfProvided(KubernetesList openShiftResources, KubernetesList kubernetesResources) throws MojoExecutionException {
        Template template = getSingletonTemplate(openShiftResources);
        if (template != null) {
            KubernetesList kubernetesTemplateList = createKubernetesTemplate(kubernetesResources, template);
            if (kubernetesTemplateList != null) {
                writeResources(kubernetesTemplateList, ResourceClassifier.KUBERNETES_TEMPLATE, generateRoute);
            }
            kubernetesResources = replaceTemplateExpressions(kubernetesResources, template);
        }
        return kubernetesResources;
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
        }
        return tempTemplate;
    }

    public Long getOpenshiftDeployTimeoutSeconds() {
        return openshiftDeployTimeoutSeconds;
    }

    public void setOpenshiftDeployTimeoutSeconds(Long openshiftDeployTimeoutSeconds) {
        this.openshiftDeployTimeoutSeconds = openshiftDeployTimeoutSeconds;
    }

    private KubernetesList removeOpenShiftObjects(KubernetesList list) {
        KubernetesListBuilder ret = new KubernetesListBuilder();
        ret.withMetadata(list.getMetadata());
        for (HasMetadata item : list.getItems()) {
            if (!isOpenshiftItem(item)) {
                ret.addToItems(item);
            } else {
                log.verbose("kubernetes.yml: Removed OpenShift specific resource '%s' of type %s",
                            KubernetesHelper.getName(item), KubernetesHelper.getKind(item));
            }
        }
        return ret.build();
    }

    private boolean isOpenshiftItem(HasMetadata item) {
        if (isTargetPlatformOpenShift(item)) {
            return true;
        }
        return item.getClass().getPackage().getName().contains("openshift");
    }

    private boolean isTargetPlatformOpenShift(HasMetadata item) {
        String targetPlatform = KubernetesHelper.getOrCreateAnnotations(item).get(TARGET_PLATFORM_ANNOTATION);
        return targetPlatform != null && "openshift".equalsIgnoreCase(targetPlatform);
    }

    private boolean isTargetPlatformKubernetes(HasMetadata item) {
        String targetPlatform = KubernetesHelper.getOrCreateAnnotations(item).get(TARGET_PLATFORM_ANNOTATION);
        return targetPlatform != null && "kubernetes".equalsIgnoreCase(targetPlatform);
    }



    private KubernetesList generateResources(List<ImageConfiguration> images)
        throws IOException, MojoExecutionException {

        // Manager for calling enrichers.
        openshiftDependencyResources = new OpenShiftDependencyResources(log);

        loadOpenShiftOverrideResources();

        EnricherContext.Builder ctxBuilder = new EnricherContext.Builder()
            .project(project)
            .session(session)
            .goalFinder(goalFinder)
            .config(extractEnricherConfig())
            .resources(resources)
            .images(resolvedImages)
            .log(log)
            .useProjectClasspath(useProjectClasspath)
            .openshiftDependencyResources(openshiftDependencyResources);
        if (resources != null) {
            ctxBuilder.namespace(resources.getNamespace());
        }
        EnricherManager enricherManager = new EnricherManager(resources, ctxBuilder.build());

        // Generate all resources from the main resource diretory, configuration and enrich them accordingly
        KubernetesListBuilder builder = generateAppResources(images, enricherManager);

        // Add resources found in subdirectories of resourceDir, with a certain profile
        // applied
        addProfiledResourcesFromSubirectories(builder, resourceDir, enricherManager);
        return builder.build();
    }

    private void loadOpenShiftOverrideResources() throws MojoExecutionException, IOException {
        openShiftOverrideResources = new OpenShiftOverrideResources(log);

        if (resourceDirOpenShiftOverride.isDirectory() && resourceDirOpenShiftOverride.exists()) {
            File[] resourceFiles = KubernetesResourceUtil.listResourceFragments(resourceDirOpenShiftOverride);
            if (resourceFiles.length > 0) {
                String defaultName = MavenUtil.createDefaultResourceName(project);
                KubernetesListBuilder builder = KubernetesResourceUtil.readResourceFragmentsFrom(
                        KubernetesResourceUtil.DEFAULT_RESOURCE_VERSIONING,
                        defaultName,
                        mavenFilterFiles(resourceFiles, this.workDirOpenShiftOverride));
                KubernetesList list = builder.build();
                for (HasMetadata item : list.getItems()) {
                    openShiftOverrideResources.addOpenShiftOverride(item);
                }
            }
        }
    }

    private void addProfiledResourcesFromSubirectories(KubernetesListBuilder builder, File resourceDir, EnricherManager enricherManager) throws IOException, MojoExecutionException {
        File[] profileDirs = resourceDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        });
        if (profileDirs != null) {
            for (File profileDir : profileDirs) {
                Profile profile = ProfileUtil.findProfile(profileDir.getName(), resourceDir);
                if (profile == null) {
                    throw new MojoExecutionException(String.format("Invalid profile '%s' given as directory in %s. " +
                                    "Please either define a profile of this name or move this directory away",
                            profileDir.getName(), resourceDir));
                }

                ProcessorConfig enricherConfig = profile.getEnricherConfig();
                File[] resourceFiles = KubernetesResourceUtil.listResourceFragments(profileDir);
                if (resourceFiles.length > 0) {
                    KubernetesListBuilder profileBuilder = readResourceFragments(resourceFiles);
                    enricherManager.createDefaultResources(enricherConfig, profileBuilder);
                    enricherManager.enrich(enricherConfig, profileBuilder);
                    KubernetesList profileItems = profileBuilder.build();
                    for (HasMetadata item : profileItems.getItems()) {
                        builder.addToItems(item);
                    }
                }
            }
        }
    }

    private KubernetesListBuilder generateAppResources(List<ImageConfiguration> images, EnricherManager enricherManager) throws IOException, MojoExecutionException {
        Path composeFilePath = checkComposeConfig();
        ComposeService composeUtil = new ComposeService(komposeBinDir, composeFilePath, log);
        try {
            File[] resourceFiles = KubernetesResourceUtil.listResourceFragments(resourceDir);
            File[] composeResourceFiles = composeUtil.convertToKubeFragments();
            File[] allResources = ArrayUtils.addAll(resourceFiles, composeResourceFiles);
            KubernetesListBuilder builder;

            // Add resource files found in the fabric8 directory
            if (allResources != null && allResources.length > 0) {
                if (resourceFiles != null && resourceFiles.length > 0) {
                    log.info("using resource templates from %s", resourceDir);
                }

                if (composeResourceFiles != null && composeResourceFiles.length > 0) {
                    log.info("using resource templates generated from compose file");
                }
                builder = readResourceFragments(allResources);
            } else {
                builder = new KubernetesListBuilder();
            }

            // Add locally configured objects
            if (resources != null) {
                // TODO: Allow also support resources to be specified via XML
                addConfiguredResources(builder, images);
            }

            // Create default resources for app resources only
            enricherManager.createDefaultResources(builder);

            // Enrich descriptors
            enricherManager.enrich(builder);

            return builder;
        } catch (ConstraintViolationException e) {
            String message = ValidationUtil.createValidationMessage(e.getConstraintViolations());
            log.error("ConstraintViolationException: %s", message);
            throw new MojoExecutionException(message, e);
        } catch (Fabric8ServiceException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            composeUtil.cleanComposeResources();
        }
    }

    private Path checkComposeConfig() {
        return composeConfigPresent() ? buildComposeFilePath() : lookDefaultComposeConfig();
    }

    private boolean composeConfigPresent() {
        return composeFile != null && composeFile.trim().length() > 0;
    }

    private Path buildComposeFilePath() {
        return Paths.get(project.getBasedir() + "/" + composeFile);
    }

    private Path lookDefaultComposeConfig() {
        File[] composeFiles = listDefaultComposeFile();
        return defaultComposeFilePresent(composeFiles) ? getFirstComposeFile(composeFiles) : null;
    }

    private File[] listDefaultComposeFile() {
        File[] composeFiles = null;
        if(composeResourceDir != null && composeResourceDir.exists()) {
            composeFiles = composeResourceDir.listFiles();
        }
        return composeFiles;
    }

    private boolean defaultComposeFilePresent(File[] composeFiles) {
        return composeFiles != null && composeFiles.length > 0;
    }

    private Path getFirstComposeFile(File[] composeFile) {
        return composeFile[0].toPath();
    }

    private KubernetesListBuilder readResourceFragments(File[] resourceFiles) throws IOException, MojoExecutionException {
        KubernetesListBuilder builder;
        String defaultName = MavenUtil.createDefaultResourceName(project);
        builder = KubernetesResourceUtil.readResourceFragmentsFrom(
            KubernetesResourceUtil.DEFAULT_RESOURCE_VERSIONING,
            defaultName,
                mavenFilterFiles(resourceFiles, this.workDir));
        return builder;
    }

    private ProcessorConfig extractEnricherConfig() throws IOException {
        return ProfileUtil.blendProfileWithConfiguration(ProfileUtil.ENRICHER_CONFIG, profile, resourceDir, enricher);
    }

    private ProcessorConfig extractGeneratorConfig() throws IOException {
        return ProfileUtil.blendProfileWithConfiguration(ProfileUtil.GENERATOR_CONFIG, profile, resourceDir, generator);
    }

    // Converts the kubernetes resources into OpenShift resources
    private KubernetesList convertToOpenShiftResources(KubernetesList resources) throws MojoExecutionException {
        KubernetesListBuilder builder = new KubernetesListBuilder();
        builder.withMetadata(resources.getMetadata());
        List<HasMetadata> items = resources.getItems();
        List<HasMetadata> objects = new ArrayList<>();
        if (items != null) {
            switchToDeployment = (switchToDeployment && isImageStreamNotUsed());
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

                item = openShiftOverrideResources.overrideResource(item);

                if(switchToDeployment && item instanceof Deployment) {
                    /*
                     * Switch to using Deployments rather than DeploymentConfig on OpenShift
                     * when not using ImageStreams.
                     */
                    objects.add(item);
                    log.info("filtering out this item, use Kubernetes Deployment instead.");
                    continue;
                }

                HasMetadata converted = convertKubernetesItemToOpenShift(item);
                if (converted != null && !isTargetPlatformKubernetes(item)) {
                    objects.add(converted);
                }
            }
        }

        openshiftDependencyResources.addMissingResources(objects);
        moveTemplatesToTopLevel(builder, objects);
        // TODO: Remove this ASAP when https://github.com/fabric8io/fabric8-maven-plugin/issues/678 is fixed
        removeInitContainers(builder, VolumePermissionEnricher.ENRICHER_NAME);
        return builder.build();
    }

    private boolean isImageStreamNotUsed() {
        return !PlatformMode.isOpenShiftMode(project.getProperties());
    }

    private void removeInitContainers(KubernetesListBuilder builder, final String enricherName) {
         builder.accept(new TypedVisitor<PodTemplateSpecBuilder>() {
             @Override
             public void visit(PodTemplateSpecBuilder builder) {
                 InitContainerHandler initContainerHandler = new InitContainerHandler(log);
                 if (initContainerHandler.hasInitContainer(builder, enricherName)) {
                     log.verbose("Removing init container from openshift.yml for %s",enricherName);
                     initContainerHandler.removeInitContainer(builder, enricherName);
                 }
             }
         });
    }

    private void moveTemplatesToTopLevel(KubernetesListBuilder builder, List<HasMetadata> objects) {
        Template template = extractAndRemoveTemplates(objects);
        if (template != null) {
            openshiftDependencyResources.addMissingParameters(template);
            builder.addToItems(template);
        } else {
            for (HasMetadata object : objects) {
                builder.addToItems(object);
            }
        }
    }

    private Template extractAndRemoveTemplates(List<HasMetadata> items) {
        Template extractedTemplate = null;
        for (HasMetadata item : new ArrayList<>(items)) {
            if (item instanceof Template && !KubernetesResourceUtil.isAppCatalogResource(item)) {
                Template template = (Template) item;
                if (extractedTemplate == null) {
                    extractedTemplate = template;
                } else {
                    extractedTemplate = Templates.combineTemplates(extractedTemplate, template);
                }
                items.remove(item);
            }
        }
        if (extractedTemplate != null) {
            extractedTemplate.setObjects(items);
        }
        return extractedTemplate;
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

        // TODO-F8SPEC: App Catalog is Fabric8 specific. Its best handled outside the 'regular' resource generation chain
        //              better in an 'AppCatalog' specific processing
        // vvvvvvv (begin)
        if (item instanceof ConfigMap &&
            "true".equals(getAnnotations(item).get(RESOURCE_APP_CATALOG_ANNOTATION))) {
            // kubernetes App Catalog so we use a Template instead on OpenShift
            return null;
        }
        // TODO-F8SPEC: ^^^^^ (end)

        // lets check if there's an OpenShift resource of this name already from a dependency...
        if (!isOpenshiftItem(item)) {
            HasMetadata dependencyResource = openshiftDependencyResources.convertKubernetesItemToOpenShift(item);
            if (dependencyResource != null) {
                return dependencyResource;
            }
        }

        KubernetesToOpenShiftConverter converter = openShiftConverters.get(item.getKind());
        return converter != null ? converter.convert(item, trimImageInContainerSpec, enableAutomaticTrigger) : item;
    }

    // ==================================================================================

    private Map<String, String> getAnnotations(HasMetadata item) {
        ObjectMeta meta = item.getMetadata();
        if (meta == null) {
            return Collections.EMPTY_MAP;
        }
        Map<String, String> annos = meta.getAnnotations();
        if (annos == null) {
            return Collections.EMPTY_MAP;
        }
        return annos;
    }

    private List<ImageConfiguration> getResolvedImages(List<ImageConfiguration> images, final Logger log) throws MojoExecutionException {
        List<ImageConfiguration> ret;
        ret = ConfigHelper.resolveImages(
            log,
            images,
            new ConfigHelper.Resolver() {
                @Override
                public List<ImageConfiguration> resolve(ImageConfiguration image) {
                    return imageConfigResolver.resolve(image, project, session);
                }
            },
            null,  // no filter on image name yet (TODO: Maybe add this, too ?)
            new ConfigHelper.Customizer() {
                @Override
                public List<ImageConfiguration> customizeConfig(List<ImageConfiguration> configs) {
                    try {
                        GeneratorContext ctx = new GeneratorContext.Builder()
                            .config(extractGeneratorConfig())
                            .project(project)
                            .session(session)
                            .goalFinder(goalFinder)
                            .goalName("fabric8:resource")
                            .logger(log)
                            .mode(mode)
                            .strategy(buildStrategy)
                            .useProjectClasspath(useProjectClasspath)
                            .build();
                        return GeneratorManager.generate(configs, ctx, true);
                    } catch (Exception e) {
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

    private void addConfiguredResources(KubernetesListBuilder builder, List<ImageConfiguration> images) {

        log.verbose("Adding resources from plugin configuration");
        addSecrets(builder);
        addServices(builder, resources.getServices());
        addController(builder, images);
    }

    private void addSecrets(KubernetesListBuilder builder) {
        log.verbose("Adding secrets resources from plugin configuration");
        List<SecretConfig> secrets = resources.getSecrets();
        if (Lists.isNullOrEmpty(secrets)) { return; }
        for (int i = 0; i < secrets.size(); i++) {
            SecretConfig secretConfig = secrets.get(i);
            if (Strings.isNullOrBlank(secretConfig.getName())) {
                log.warn("Secret name is empty. You should provide a proper name for the secret");
                continue;
            }

            Map<String, String> data = new HashMap<>();
            String type = "";
            ObjectMeta metadata = new ObjectMetaBuilder()
                    .withNamespace(secretConfig.getNamespace())
                    .withName(secretConfig.getName())
                    .build();

            // docker-registry
            if (secretConfig.getDockerServerId() != null) {
                String dockerSecret = DockerServerUtil.getDockerJsonConfigString(settings, secretConfig.getDockerServerId());
                if (Strings.isNullOrBlank(dockerSecret)) {
                    log.warn("Docker secret with id " + secretConfig.getDockerServerId() + " cannot be found in maven settings");
                    continue;
                }
                data.put(SecretConstants.DOCKER_DATA_KEY, Base64Util.encodeToString(dockerSecret));
                type = SecretConstants.DOCKER_CONFIG_TYPE;
            }
            // TODO: generic secret (not supported for now)

            if (Strings.isNullOrBlank(type) || data.isEmpty()) {
                log.warn("No data can be found for docker secret with id " + secretConfig.getDockerServerId());
                continue;
            }

            Secret secret = new SecretBuilder().withData(data).withMetadata(metadata).withType(type).build();
            builder.addToSecretItems(i, secret);
        }
    }

    private void addController(KubernetesListBuilder builder, List<ImageConfiguration> images) {
        // TODO: Change to ReplicaSet
        ReplicationControllerHandler rcHandler = handlerHub.getReplicationControllerHandler();
        if (resources.getControllerName() != null) {
            builder.addToReplicationControllerItems(rcHandler.getReplicationController(resources, images));
        }
    }

    private File[] mavenFilterFiles(File[] resourceFiles, File outDir) throws MojoExecutionException {
        if (!outDir.exists()) {
            if (!outDir.mkdirs()) {
                throw new MojoExecutionException("Cannot create working dir " + outDir);
            }
        }
        File[] ret = new File[resourceFiles.length];
        int i = 0;
        for (File resource : resourceFiles) {
            File targetFile = new File(outDir, resource.getName());
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
            return ((ArrayList<Service>) services).toArray(new Service[services.size()]);
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
