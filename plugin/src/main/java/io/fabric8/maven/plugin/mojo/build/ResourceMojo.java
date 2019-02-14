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
package io.fabric8.maven.plugin.mojo.build;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.core.access.ClusterAccess;
import io.fabric8.maven.core.config.MappingConfig;
import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.config.Profile;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.config.RuntimeMode;
import io.fabric8.maven.core.handler.HandlerHub;
import io.fabric8.maven.core.model.GroupArtifactVersion;
import io.fabric8.maven.core.util.FileUtil;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.core.util.ProfileUtil;
import io.fabric8.maven.core.util.ResourceClassifier;
import io.fabric8.maven.core.util.ResourceFileType;
import io.fabric8.maven.core.util.ResourceUtil;
import io.fabric8.maven.core.util.ValidationUtil;
import io.fabric8.maven.core.util.kubernetes.KubernetesHelper;
import io.fabric8.maven.core.util.kubernetes.KubernetesResourceUtil;
import io.fabric8.maven.core.util.validator.ResourceValidator;
import io.fabric8.maven.docker.AbstractDockerMojo;
import io.fabric8.maven.docker.config.ConfigHelper;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.handler.ImageConfigResolver;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageNameFormatter;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import io.fabric8.maven.generator.api.GeneratorContext;
import io.fabric8.maven.plugin.converter.DeploymentConfigOpenShiftConverter;
import io.fabric8.maven.plugin.converter.DeploymentOpenShiftConverter;
import io.fabric8.maven.plugin.converter.KubernetesToOpenShiftConverter;
import io.fabric8.maven.plugin.converter.NamespaceOpenShiftConverter;
import io.fabric8.maven.plugin.converter.ReplicSetOpenShiftConverter;
import io.fabric8.maven.plugin.enricher.EnricherManager;
import io.fabric8.maven.plugin.generator.GeneratorManager;
import io.fabric8.maven.plugin.mojo.AbstractFabric8Mojo;
import io.fabric8.maven.plugin.mojo.ResourceDirCreator;
import io.fabric8.openshift.api.model.Template;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.validation.ConstraintViolationException;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenFilteringException;

import static io.fabric8.maven.core.util.ResourceFileType.yaml;
import static io.fabric8.maven.plugin.mojo.build.ApplyMojo.DEFAULT_OPENSHIFT_MANIFEST;

/**
 * Generates or copies the Kubernetes JSON file and attaches it to the build so its
 * installed and released to maven repositories like other build artifacts.
 */
@Mojo(name = "resource", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresDependencyResolution = ResolutionScope.COMPILE)
public class ResourceMojo extends AbstractFabric8Mojo {
    /**
     * Used to annotate a resource as being for a specific platform only such as "kubernetes" or "openshift"
     */

    // THe key how we got the the docker maven plugin
    private static final String DOCKER_MAVEN_PLUGIN_KEY = "io.fabric8:docker-maven-plugin";
    private static final String DOCKER_IMAGE_USER = "docker.image.user";
    /**
     * The generated kubernetes and openshift manifests
     */
    @Parameter(property = "fabric8.targetDir", defaultValue = "${project.build.outputDirectory}/META-INF/fabric8")
    protected File targetDir;

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
     * Environment name where resources are placed. For example, if you set this property to dev and resourceDir is the default one, Fabric8 will look at src/main/fabric8/dev
     * Same applies for resourceDirOpenShiftOverride property.
     */
    @Parameter(property = "fabric8.environment")
    private String environment;

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
    private RuntimeMode mode = RuntimeMode.DEFAULT;

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

    // Resource specific configuration for this plugin
    @Parameter(property = "fabric8.gitRemote")
    private String gitRemote;

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

    // Mapping for kind filenames
    @Parameter
    private List<MappingConfig> mappings;

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

    @Parameter(property = "docker.skip.resource", defaultValue = "false")
    protected boolean skipResource;

    // Access for creating OpenShift binary builds
    private ClusterAccess clusterAccess;

    private RuntimeMode runtimeMode;

    /**
     * The artifact type for attaching the generated resource file to the project.
     * Can be either 'json' or 'yaml'
     */
    @Parameter(property = "fabric8.resourceType")
    private ResourceFileType resourceFileType = yaml;
    @Component
    private MavenProjectHelper projectHelper;

    // resourceDir when environment has been applied
    private File realResourceDir;

    // resourceDirOpenShiftOverride when environment has been applied
    private File realResourceDirOpenShiftOverride;

    /**
     * Returns the Template if the list contains a single Template only otherwise returns null
     */
    protected static Template getSingletonTemplate(KubernetesList resources) {
        // if the list contains a single Template lets unwrap it
        if (resources != null) {
            List<HasMetadata> items = resources.getItems();
            if (items != null && items.size() == 1) {
                HasMetadata singleEntity = items.get(0);
                if (singleEntity instanceof Template) {
                    return (Template) singleEntity;
                }
            }
        }
        return null;
    }

    public static File writeResourcesIndividualAndComposite(KubernetesList resources, File resourceFileBase,
        ResourceFileType resourceFileType, Logger log, Boolean generateRoute) throws MojoExecutionException {

        //Creating a new items list. This will be used to generate openshift.yml
        List<HasMetadata> newItemList = new ArrayList<>();

        if (!generateRoute) {

            //if flag is set false, this will remove the Route resource from resources list
            for (HasMetadata item : resources.getItems()) {
                if (item.getKind().equalsIgnoreCase("Route")) {
                    continue;
                }
                newItemList.add(item);
            }

            //update the resource with new list
            resources.setItems(newItemList);
        }

        // entity is object which will be sent to writeResource for openshift.yml
        // if generateRoute is false, this will be set to resources with new list
        // otherwise it will be set to resources with old list.
        Object entity = resources;

        // if the list contains a single Template lets unwrap it
        // in resources already new or old as per condition is set.
        // no need to worry about this for dropping Route.
        Template template = getSingletonTemplate(resources);
        if (template != null) {
            entity = template;
        }

        File file = writeResource(resourceFileBase, entity, resourceFileType);

        // write separate files, one for each resource item
        // resources passed to writeIndividualResources is also new one.
        writeIndividualResources(resources, resourceFileBase, resourceFileType, log, generateRoute);
        return file;
    }

    private static void writeIndividualResources(KubernetesList resources, File targetDir,
        ResourceFileType resourceFileType, Logger log, Boolean generateRoute) throws MojoExecutionException {
        for (HasMetadata item : resources.getItems()) {
            String name = KubernetesHelper.getName(item);
            if (StringUtils.isBlank(name)) {
                log.error("No name for generated item %s", item);
                continue;
            }
            String itemFile = KubernetesResourceUtil.getNameWithSuffix(name, item.getKind());

            // Here we are writing individual file for all the resources.
            // if generateRoute is false and resource is route, we should not generate it.

            if (!(item.getKind().equalsIgnoreCase("Route") && !generateRoute)) {
                File itemTarget = new File(targetDir, itemFile);
                writeResource(itemTarget, item, resourceFileType);
            }
        }
    }

    private static File writeResource(File resourceFileBase, Object entity, ResourceFileType resourceFileType)
        throws MojoExecutionException {
        try {
            return ResourceUtil.save(resourceFileBase, entity, resourceFileType);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write resource to " + resourceFileBase + ". " + e, e);
        }
    }

    public void executeInternal() throws MojoExecutionException, MojoFailureException {
        if (skipResource) {
            return;
        }

        realResourceDir = ResourceDirCreator.getFinalResourceDir(resourceDir, environment);
        realResourceDirOpenShiftOverride = ResourceDirCreator.getFinalResourceDir(resourceDirOpenShiftOverride, environment);

        clusterAccess = new ClusterAccess(getClusterConfiguration());
        updateKindFilenameMappings();
        try {
            lateInit();
            final File remoteResources = resolveRemoteResources();
            // Resolve the Docker image build configuration
            resolvedImages = getResolvedImages(images, log);
            if (!skip && (!isPomProject() || hasFabric8Dir())) {
                // Extract and generate resources which can be a mix of Kubernetes and OpenShift resources

                KubernetesList resources;
                for(PlatformMode platformMode : new PlatformMode[] { PlatformMode.kubernetes, PlatformMode.openshift }) {
                    ResourceClassifier resourceClassifier = platformMode == PlatformMode.kubernetes ? ResourceClassifier.KUBERNETES
                            : ResourceClassifier.OPENSHIFT;

                    resources = generateResources(platformMode, resolvedImages, remoteResources);
                    writeResources(resources, resourceClassifier, generateRoute);
                    File resourceDir = new File(this.targetDir, resourceClassifier.getValue());
                    validateIfRequired(resourceDir, resourceClassifier);
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate fabric8 descriptor", e);
        }
    }

    private File resolveRemoteResources() {
        if (this.resources != null) {
            final List<String> remotes = this.resources.getRemotes();
            if (remotes != null && !remotes.isEmpty()) {
                final File tempDirectory = FileUtil.createTempDirectory();
                FileUtil.downloadRemotes(tempDirectory, remotes, this.log);
                return tempDirectory;
            }
        }

        return null;
    }

    private void updateKindFilenameMappings() {
        if (mappings != null) {
            final Map<String, List<String>> mappingKindFilename = new HashMap<>();
            for (MappingConfig mappingConfig : this.mappings) {
                if (mappingConfig.isValid()) {
                    mappingKindFilename.put(mappingConfig.getKind(), Arrays.asList(mappingConfig.getFilenamesAsArray()));
                } else {
                    throw new IllegalArgumentException(String.format("Invalid mapping for Kind %s and Filename Types %s",
                        mappingConfig.getKind(), mappingConfig.getFilenameTypes()));
                }
            }
            KubernetesResourceUtil.updateKindFilenameMapper(mappingKindFilename);
        }
    }

    private void validateIfRequired(File resourceDir, ResourceClassifier classifier)
        throws MojoExecutionException, MojoFailureException {
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

    private void lateInit() {
        runtimeMode = clusterAccess.resolveRuntimeMode(mode, log);
        log.info("Running in [[B]]%s[[B]] mode", runtimeMode.getLabel());

        if (isOpenShiftMode()) {
            Properties properties = project.getProperties();
            if (!properties.contains(DOCKER_IMAGE_USER)) {
                String namespace = clusterAccess.getNamespace();
                log.info("Using docker image name of namespace: " + namespace);
                properties.setProperty(DOCKER_IMAGE_USER, namespace);
            }
            if (!properties.contains(PlatformMode.FABRIC8_EFFECTIVE_PLATFORM_MODE)) {
                properties.setProperty(PlatformMode.FABRIC8_EFFECTIVE_PLATFORM_MODE, runtimeMode.toString());
            }
        }

        openShiftConverters = new HashMap<>();
        openShiftConverters.put("ReplicaSet", new ReplicSetOpenShiftConverter());
        openShiftConverters.put("Deployment",
            new DeploymentOpenShiftConverter(runtimeMode, getOpenshiftDeployTimeoutSeconds()));
        // TODO : This converter shouldn't be here. See its javadoc.
        openShiftConverters.put("DeploymentConfig",
            new DeploymentConfigOpenShiftConverter(getOpenshiftDeployTimeoutSeconds()));
        openShiftConverters.put("Namespace", new NamespaceOpenShiftConverter());

        handlerHub = new HandlerHub(
            new GroupArtifactVersion(project.getGroupId(), project.getArtifactId(), project.getVersion()),
            project.getProperties());
    }

    private boolean isOpenShiftMode() {
        return runtimeMode.equals(RuntimeMode.openshift);
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

    private KubernetesList generateResources(PlatformMode platformMode, List<ImageConfiguration> images, File remoteResources)
        throws IOException, MojoExecutionException {

        // Manager for calling enrichers.
        MavenEnricherContext.Builder ctxBuilder = new MavenEnricherContext.Builder()
                .runtimeMode(mode)
                .project(project)
                .session(session)
                .config(extractEnricherConfig())
                .settings(settings)
                .properties(project.getProperties())
                .resources(resources)
                .images(resolvedImages)
                .log(log);

        EnricherManager enricherManager = new EnricherManager(resources, ctxBuilder.build(),
            MavenUtil.getCompileClasspathElementsIfRequested(project, useProjectClasspath));

        // Generate all resources from the main resource directory, configuration and enrich them accordingly
        KubernetesListBuilder builder = generateAppResources(platformMode, images, enricherManager, remoteResources);

        // Add resources found in subdirectories of resourceDir, with a certain profile
        // applied
        addProfiledResourcesFromSubirectories(platformMode, builder, realResourceDir, enricherManager);
        return builder.build();
    }

    private void addProfiledResourcesFromSubirectories(PlatformMode platformMode, KubernetesListBuilder builder, File resourceDir,
        EnricherManager enricherManager) throws IOException, MojoExecutionException {
        File[] profileDirs = resourceDir.listFiles((File pathname) -> pathname.isDirectory());
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
                    KubernetesListBuilder profileBuilder = readResourceFragments(platformMode, resourceFiles);
                    enricherManager.createDefaultResources(platformMode, enricherConfig, profileBuilder);
                    enricherManager.enrich(platformMode, enricherConfig, profileBuilder);
                    KubernetesList profileItems = profileBuilder.build();
                    for (HasMetadata item : profileItems.getItems()) {
                        builder.addToItems(item);
                    }
                }
            }
        }
    }

    private KubernetesListBuilder generateAppResources(PlatformMode platformMode, List<ImageConfiguration> images, EnricherManager enricherManager,
        File remoteResources)
        throws IOException, MojoExecutionException {
        try {
            KubernetesListBuilder builder = processResourceFragments(platformMode, remoteResources);

            // Create default resources for app resources only

            enricherManager.createDefaultResources(platformMode, builder);

            // Enrich descriptors
            enricherManager.enrich(platformMode, builder);

            return builder;
        } catch (ConstraintViolationException e) {
            String message = ValidationUtil.createValidationMessage(e.getConstraintViolations());
            log.error("ConstraintViolationException: %s", message);
            throw new MojoExecutionException(message, e);
        }
    }

    private KubernetesListBuilder processResourceFragments(PlatformMode platformMode, File remoteResources) throws IOException, MojoExecutionException {
        File[] resourceFiles = KubernetesResourceUtil.listResourceFragments(realResourceDir);
        if (remoteResources != null && remoteResources.isDirectory()) {
            final File[] remoteFragments = remoteResources.listFiles();
            resourceFiles = ArrayUtils.addAll(resourceFiles, remoteFragments);
        }
        KubernetesListBuilder builder;

        // Add resource files found in the fabric8 directory
        if (resourceFiles != null && resourceFiles.length > 0) {
            if (resourceFiles != null && resourceFiles.length > 0) {
                log.info("using resource templates from %s", realResourceDir);
            }

            builder = readResourceFragments(platformMode, resourceFiles);
        } else {
            builder = new KubernetesListBuilder();
        }
        return builder;
    }

    private KubernetesListBuilder readResourceFragments(PlatformMode platformMode, File[] resourceFiles) throws IOException, MojoExecutionException {
        KubernetesListBuilder builder;
        String defaultName = MavenUtil.createDefaultResourceName(project.getArtifactId());
        builder = KubernetesResourceUtil.readResourceFragmentsFrom(
            platformMode,
            KubernetesResourceUtil.DEFAULT_RESOURCE_VERSIONING,
            defaultName,
            mavenFilterFiles(resourceFiles, this.workDir));
        return builder;
    }

    private ProcessorConfig extractEnricherConfig() throws IOException {
        return ProfileUtil.blendProfileWithConfiguration(ProfileUtil.ENRICHER_CONFIG, profile, realResourceDir, enricher);
    }

    private ProcessorConfig extractGeneratorConfig() throws IOException {
        return ProfileUtil.blendProfileWithConfiguration(ProfileUtil.GENERATOR_CONFIG, profile, realResourceDir, generator);
    }

    // ==================================================================================

    private List<ImageConfiguration> getResolvedImages(List<ImageConfiguration> images, final Logger log)
        throws MojoExecutionException {
        List<ImageConfiguration> ret;
        ret = ConfigHelper.resolveImages(
            log,
            images,
            (ImageConfiguration image) -> imageConfigResolver.resolve(image, project, session),
            null,  // no filter on image name yet (TODO: Maybe add this, too ?)
                (List<ImageConfiguration> configs) -> {
                    try {
                        GeneratorContext ctx = new GeneratorContext.Builder()
                            .config(extractGeneratorConfig())
                            .project(project)
                            .logger(log)
                            .platformMode(mode)
                            .strategy(buildStrategy)
                            .useProjectClasspath(useProjectClasspath)
                            .build();
                        return GeneratorManager.generate(configs, ctx, true);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Cannot extract generator: " + e, e);
                    }
            });

        Date now = getBuildReferenceDate();
        storeReferenceDateInPluginContext(now);
        String minimalApiVersion = ConfigHelper.initAndValidate(ret, null /* no minimal api version */,
            new ImageNameFormatter(project, now), log);
        return ret;
    }

    private void storeReferenceDateInPluginContext(Date now) {
        Map<String, Object> pluginContext = getPluginContext();
        pluginContext.put(AbstractDockerMojo.CONTEXT_KEY_BUILD_TIMESTAMP, now);
    }

    // get a reference date
    private Date getBuildReferenceDate() throws MojoExecutionException {
        // Pick up an existing build date created by fabric8:build previously
        File tsFile = new File(project.getBuild().getDirectory(), AbstractDockerMojo.DOCKER_BUILD_TIMESTAMP);
        if (!tsFile.exists()) {
            return new Date();
        }
        try {
            return EnvUtil.loadTimestamp(tsFile);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot read timestamp from " + tsFile, e);
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

    private boolean hasFabric8Dir() {
        return realResourceDir.isDirectory();
    }

    private boolean isPomProject() {
        return "pom".equals(project.getPackaging());
    }

    protected void writeResources(KubernetesList resources, ResourceClassifier classifier, Boolean generateRoute)
        throws MojoExecutionException {
        // write kubernetes.yml / openshift.yml
        File resourceFileBase = new File(this.targetDir, classifier.getValue());

        File file =
            writeResourcesIndividualAndComposite(resources, resourceFileBase, this.resourceFileType, log, generateRoute);

        // Attach it to the Maven reactor so that it will also get deployed
        projectHelper.attachArtifact(project, this.resourceFileType.getArtifactType(), classifier.getValue(), file);
    }
}
