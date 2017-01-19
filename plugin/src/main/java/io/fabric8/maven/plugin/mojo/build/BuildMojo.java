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
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.core.access.ClusterAccess;
import io.fabric8.maven.core.config.BuildRecreateMode;
import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.openshift.ImageStreamService;
import io.fabric8.maven.core.openshift.BuildService;
import io.fabric8.maven.core.util.GoalFinder;
import io.fabric8.maven.core.util.Gofabric8Util;
import io.fabric8.maven.core.util.OpenShiftDependencyResources;
import io.fabric8.maven.core.util.ProfileUtil;
import io.fabric8.maven.core.util.ResourceFileType;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.access.DockerConnectionDetector;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.MojoParameters;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.plugin.enricher.EnricherManager;
import io.fabric8.maven.plugin.generator.GeneratorManager;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigSpec;
import io.fabric8.openshift.api.model.BuildOutput;
import io.fabric8.openshift.api.model.BuildOutputBuilder;
import io.fabric8.openshift.api.model.BuildSource;
import io.fabric8.openshift.api.model.BuildStrategy;
import io.fabric8.openshift.api.model.BuildStrategyBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static io.fabric8.maven.plugin.mojo.build.ApplyMojo.DEFAULT_OPENSHIFT_MANIFEST;
import static io.fabric8.maven.plugin.mojo.build.ApplyMojo.loadResources;

/**
 * Builds the docker images configured for this project via a Docker or S2I binary build.
 *
 * @author roland
 * @since 16/03/16
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresDependencyResolution = ResolutionScope.COMPILE)
public class BuildMojo extends io.fabric8.maven.docker.BuildMojo {

    /**
     * Generator specific options. This is a generic prefix where the keys have the form
     * <code>&lt;generator-prefix&gt;-&lt;option&gt;</code>.
     */
    @Parameter
    private ProcessorConfig generator;

    /**
     * Enrichers used for enricher build objects
     */
    @Parameter
    private ProcessorConfig enricher;

    /**
     * Resource config for getting annotation and labels to be applied to enriched build objects
     */
    @Parameter
    private ResourceConfig resources;

    // To skip over the execution of the goal
    @Parameter(property = "fabric8.skip", defaultValue = "false")
    protected boolean skip;

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
     * Folder where to find project specific files, e.g a custom profile
     */
    @Parameter(property = "fabric8.resourceDir", defaultValue = "${basedir}/src/main/fabric8")
    private File resourceDir;

    @Parameter(property = "fabric8.skip.build.pom", defaultValue = "true")
    private boolean skipBuildPom;

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
     * The S2I binary builder BuildConfig name suffix appended to the image name to avoid
     * clashing with the underlying BuildConfig for the Jenkins pipeline
     */
    @Parameter(property = "fabric8.s2i.buildNameSuffix", defaultValue = "-s2i")
    private String s2iBuildNameSuffix;

    /**
     * Should we use the project's compile-time classpath to scan for additional enrichers/generators?
     */
    @Parameter(property = "fabric8.useProjectClasspath", defaultValue = "false")
    private boolean useProjectClasspath = false;

    /**
     * How to recreate the build config and/or image stream created by the build.
     * Only in effect when <code>mode == openshift</code> or mode is <code>auto</code>
     * and openshift is detected. If not set, existing
     * build config will not be recreated.
     *
     * The possible values are:
     *
     * <ul>
     *   <li><strong>buildConfig</strong> or <strong>bc</strong> :
     *       Only the build config is recreated</li>
     *   <li><strong>imageStream</strong> or <strong>is</strong> :
     *       Only the image stream is recreated</li>
     *   <li><strong>all</strong> : Both, build config and image stream are recreated</li>
     *   <li><strong>none</strong> : Neither build config nor image stream is recreated</li>
     * </ul>
     */
    @Parameter(property = "fabric8.build.recreate", defaultValue = "none")
    private String buildRecreate;

    /**
     * Namespace to use when accessing Kubernetes or OpenShift
     */
    @Parameter(property = "fabric8.namespace")
    private String namespace;

    // Used for determining which mojos are called during a run
    @Component
    protected GoalFinder goalFinder;

    // Access for creating OpenShift binary builds
    private ClusterAccess clusterAccess;

    // Mode which is resolved, also when 'auto' is set
    private PlatformMode platformMode;

    private OpenShiftDependencyResources openshiftDependencyResources;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            return;
        }
        clusterAccess = new ClusterAccess(namespace);
        // Platform mode is already used in executeInternal()
        super.execute();
    }

    @Override
    protected boolean isDockerAccessRequired() {
        return platformMode == PlatformMode.kubernetes;
    }

    @Override
    protected void executeInternal(ServiceHub hub) throws DockerAccessException, MojoExecutionException {
        if (project != null && skipBuildPom && Objects.equals("pom", project.getPackaging())) {
            getLog().debug("Disabling docker build for pom packaging");
            return;
        }
        if (getResolvedImages().size() == 0) {
            log.warn("No image build configuration found or detected");
        }
        super.executeInternal(hub);
    }

    public BuildMojo() {
        super();
    }

    @Override
    protected List<DockerConnectionDetector.DockerHostProvider> getDockerHostProviders() {
        return Gofabric8Util.extractDockerHostProvider(log);
    }

    @Override
    protected void buildAndTag(ServiceHub hub, ImageConfiguration imageConfig)
        throws MojoExecutionException, DockerAccessException {
        try {
            if (platformMode == PlatformMode.kubernetes) {
                super.buildAndTag(hub, imageConfig);
            } else if (platformMode == PlatformMode.openshift) {
                executeOpenShiftBuild(hub, imageConfig);
            } else {
                throw new MojoExecutionException("Unknown platform mode " + mode + " for image " + imageConfig.getDescription());
            }
        } catch (IOException e) {
            throw new MojoExecutionException("I/O Error executing build for image " + imageConfig.getDescription() + ":" + e,e);
        }
    }

    /**
     * Customization hook called by the base plugin.
     *
     * @param configs configuration to customize
     * @return the configuration customized by our generators.
     */
    @Override
    public List<ImageConfiguration> customizeConfig(List<ImageConfiguration> configs) {
        platformMode = clusterAccess.resolvePlatformMode(mode, log);
        if (platformMode == PlatformMode.openshift) {
            log.info("Using [[B]]OpenShift[[B]] build with strategy [[B]]%s[[B]]", buildStrategy.getLabel());
        } else {
            log.info("Building Docker image in [[B]]Kubernetes[[B]] mode");
        }

        if (platformMode.equals(PlatformMode.openshift)) {
            Properties properties = project.getProperties();
            if (!properties.contains(PlatformMode.FABRIC8_EFFECTIVE_PLATFORM_MODE)) {
                properties.setProperty(PlatformMode.FABRIC8_EFFECTIVE_PLATFORM_MODE, platformMode.toString());
            }
        }

        try {
            return GeneratorManager.generate(configs, extractGeneratorConfig(), project, session, goalFinder, "fabric8:build", log, platformMode, buildStrategy, useProjectClasspath);
        } catch (MojoExecutionException e) {
            throw new IllegalArgumentException("Cannot extract generator config: " + e, e);
        }
    }

    @Override
    protected String getLogPrefix() {
        return "F8: ";
    }

    // ==================================================================================================

    // Get generator config
    private ProcessorConfig extractGeneratorConfig() {
        try {
            return generator != null ? generator : ProfileUtil.extractProcesssorConfiguration(ProfileUtil.GENERATOR_CONFIG, profile, resourceDir);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot extract generator config: " + e,e);
        }
    }

    // Docker build with a binary source strategy
    private void executeOpenShiftBuild(ServiceHub hub, ImageConfiguration imageConfig) throws MojoExecutionException, IOException {
        MojoParameters params = createMojoParameters();
        ImageName imageName = new ImageName(imageConfig.getName());

        // Create tar file with Docker archive
        File dockerTar = hub.getArchiveService().createDockerBuildArchive(imageConfig, params);

        OpenShiftClient client = getOpenShiftClient();

        KubernetesListBuilder builder = new KubernetesListBuilder();

        // Check for buildconfig / imagestream and create them if necessary
        String buildName = updateOrCreateBuildConfig(client, builder, imageConfig);
        checkOrCreateImageStream(client, builder, getImageStreamName(imageName));

        applyResourceObjects(client, builder);

        // Start the actual build
        BuildService buildService = new BuildService(client, log);
        Build build = buildService.startBuild(client, dockerTar, buildName);

        // Wait until the build finishes
        buildService.waitForOpenShiftBuildToComplete(client, build);

        // Create a file with generated image streams
        saveImageStreamToFile(imageName, client);
    }

    private void saveImageStreamToFile(ImageName imageName, OpenShiftClient client) throws MojoExecutionException {
        File imageStreamFile = ResourceFileType.yaml.addExtension(new File(project.getBuild().getDirectory(),
                                                                           imageName.getSimpleName() + "-is"));
        ImageStreamService imageStreamHandler = new ImageStreamService(client, log);
        imageStreamHandler.saveImageStreamResource(imageName, imageStreamFile);
    }

    private String getS2IBuildName(ImageName imageName) {
        return imageName.getSimpleName() + s2iBuildNameSuffix;
    }

    private String getImageStreamName(ImageName name) {
        return name.getSimpleName();
    }

    // Create the openshift client
    private OpenShiftClient getOpenShiftClient() throws MojoExecutionException {
        OpenShiftClient client = clusterAccess.createOpenShiftClient();
        if (!KubernetesHelper.isOpenShift(client)) {
            throw new MojoExecutionException(
                "Cannot create OpenShift Docker build with a non-OpenShift cluster at " + client.getMasterUrl());
        }
        return client;
    }


    private void applyResourceObjects(OpenShiftClient client, KubernetesListBuilder builder) throws IOException {
        if (builder.getItems().size() > 0) {
            enrich(builder);
            KubernetesList k8sList = builder.build();
            client.lists().create(k8sList);
        }
    }

    // Build up an enricher manager to enrich also our implicit created build ojects
    private void enrich(KubernetesListBuilder builder) throws IOException {
        ProcessorConfig resolvedEnricherConfig = enricher != null ? enricher : ProfileUtil.extractProcesssorConfiguration(ProfileUtil.ENRICHER_CONFIG, profile, resourceDir);
        openshiftDependencyResources = new OpenShiftDependencyResources(log);
        EnricherContext enricherContext = new EnricherContext(project, session, goalFinder, resolvedEnricherConfig, getResolvedImages(), resources, log, useProjectClasspath, openshiftDependencyResources);
        EnricherManager enricherManager = new EnricherManager(enricherContext);
        enricherManager.enrich(builder);
    }

    private void checkOrCreateImageStream(OpenShiftClient client, KubernetesListBuilder builder, String imageStreamName) {
        boolean hasImageStream = client.imageStreams().withName(imageStreamName).get() != null;
        if (hasImageStream && getBuildRecreateMode().isImageStream()) {
            client.imageStreams().withName(imageStreamName).delete();
            hasImageStream = false;
        }
        if (!hasImageStream) {
            log.info("Creating ImageStream %s", imageStreamName);
            builder.addNewImageStreamItem()
                     .withNewMetadata()
                       .withName(imageStreamName)
                     .endMetadata()
                   .endImageStreamItem();
        } else {
            log.info("Adding to ImageStream %s", imageStreamName);
        }
    }

    private BuildRecreateMode getBuildRecreateMode() {
        return BuildRecreateMode.fromParameter(buildRecreate);
    }

    private String updateOrCreateBuildConfig(OpenShiftClient client, KubernetesListBuilder builder, ImageConfiguration imageConfig) {
        ImageName imageName = new ImageName(imageConfig.getName());
        String buildName = getS2IBuildName(imageName);
        String imageStreamName = getImageStreamName(imageName);
        String outputImageStreamTag = imageStreamName + ":" + imageName.getTag();

        BuildStrategy buildStrategyResource = createBuildStrategy(imageConfig, buildStrategy);
        BuildOutput buildOutput = new BuildOutputBuilder().withNewTo()
                .withKind("ImageStreamTag")
                .withName(outputImageStreamTag)
                .endTo().build();

        // Fetch exsting build config
        BuildConfig buildConfig = client.buildConfigs().withName(buildName).get();
        if (buildConfig != null) {
            // lets verify the BC
            BuildConfigSpec spec = getBuildConfigSpec(buildConfig);
            validateSourceType(buildName, spec);

            if (getBuildRecreateMode().isBuildConfig()) {
                // Delete and recreate afresh
                client.buildConfigs().withName(buildName).delete();
                return createBuildConfig(builder, buildName, buildStrategyResource, buildOutput);
            } else {
                // Update & return
                return updateBuildConfig(client, buildName, buildStrategyResource, buildOutput, spec);
            }
        } else {
            // Create afresh
            return createBuildConfig(builder, buildName, buildStrategyResource, buildOutput);
        }
    }

    private String createBuildConfig(KubernetesListBuilder builder, String buildName, BuildStrategy buildStrategyResource, BuildOutput buildOutput) {
        log.info("Creating BuildConfig %s for %s build", buildName, buildStrategyResource.getType());
        builder.addNewBuildConfigItem()
                     .withNewMetadata()
                       .withName(buildName)
                     .endMetadata()
                     .withNewSpec()
                       .withNewSource()
                          .withType("Binary")
                       .endSource()
                       .withStrategy(buildStrategyResource)
                       .withOutput(buildOutput)
                     .endSpec()
                   .endBuildConfigItem();
        return buildName;
    }

    private String updateBuildConfig(OpenShiftClient client, String buildName, BuildStrategy buildStrategy,
                                     BuildOutput buildOutput, BuildConfigSpec spec) {
        // lets check if the strategy or output has changed and if so lets update the BC
        // e.g. the S2I builder image or the output tag and
        if (!Objects.equals(buildStrategy, spec.getStrategy()) || !Objects.equals(buildOutput, spec.getOutput())) {
            client.buildConfigs().withName(buildName).edit()
                  .editSpec()
                  .withStrategy(buildStrategy)
                  .withOutput(buildOutput)
                  .endSpec()
                  .done();
            log.info("Updating BuildConfig %s for %s build", buildName, buildStrategy.getType());
        } else {
            log.info("Using BuildConfig %s for %s build", buildName, buildStrategy.getType());
        }
        return buildName;
    }

    private BuildConfigSpec getBuildConfigSpec(BuildConfig buildConfig) {
        BuildConfigSpec spec = buildConfig.getSpec();
        if (spec == null) {
            spec = new BuildConfigSpec();
            buildConfig.setSpec(spec);
        }
        return spec;
    }

    private void validateSourceType(String buildName, BuildConfigSpec spec) {
        BuildSource source = spec.getSource();
        if (source != null) {
            String sourceType = source.getType();
            if (!Objects.equals("Binary", sourceType)) {
                log.warn("BuildConfig %s is not of type: 'Binary' but is '%s' !", buildName, sourceType);
            }
        }
    }

    private BuildStrategy createBuildStrategy(ImageConfiguration imageConfig, OpenShiftBuildStrategy osBuildStrategy) {
        if (osBuildStrategy == OpenShiftBuildStrategy.docker) {
            return new BuildStrategyBuilder().withType("Docker").build();
        } else if (osBuildStrategy == OpenShiftBuildStrategy.s2i) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            Map<String, String> fromExt = buildConfig.getFromExt();

            String fromName = getMapValueWithDefault(fromExt, OpenShiftBuildStrategy.SourceStrategy.name, buildConfig.getFrom());
            String fromNamespace = getMapValueWithDefault(fromExt, OpenShiftBuildStrategy.SourceStrategy.namespace, "");
            String fromKind = getMapValueWithDefault(fromExt, OpenShiftBuildStrategy.SourceStrategy.kind, "DockerImage");

            if ("ImageStreamTag".equals(fromKind) && fromNamespace == null) {
                fromNamespace = "openshift";
            }
            if (fromNamespace.isEmpty()) {
                fromNamespace = null;
            }

            return new BuildStrategyBuilder()
                .withType("Source")
                .withNewSourceStrategy()
                  .withNewFrom()
                     .withKind(fromKind)
                     .withName(fromName)
                     .withNamespace(fromNamespace)
                  .endFrom()
                .endSourceStrategy()
                .build();
        } else {
            throw new IllegalArgumentException("Unsupported BuildStrategy " + osBuildStrategy);
        }
    }

    private String getMapValueWithDefault(Map<String, String> map, OpenShiftBuildStrategy.SourceStrategy strategy, String defaultValue) {
        return getMapValueWithDefault(map, strategy.key(), defaultValue);
    }

    private String getMapValueWithDefault(Map<String, String> map, String field, String defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        String value = map.get(field);
        return value != null ? value : defaultValue;
    }

}
