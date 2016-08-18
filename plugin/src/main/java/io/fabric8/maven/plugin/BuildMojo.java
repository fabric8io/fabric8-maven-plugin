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


import java.io.*;
import java.util.List;
import java.util.Objects;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.core.access.ClusterAccess;
import io.fabric8.maven.core.config.*;
import io.fabric8.maven.core.util.ProfileUtil;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.MojoParameters;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.plugin.enricher.EnricherManager;
import io.fabric8.maven.plugin.generator.GeneratorManager;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildStrategy;
import io.fabric8.openshift.api.model.BuildStrategyBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;

/**
 * Proxy to d-m-p's build Mojo
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
     * Resource config for getting annotation and labels to be apllied to enriched build objects
     */
    @Parameter
    private ResourceConfig resources;

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
     * Whether to perform a Kubernetes build (i.e. agains a vanilla Docker daemon) or
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
     * Should we use the project's compmile-time classpath to scan for additional enrichers/generators?
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

    // Access for creating OpenShift binary builds
    private ClusterAccess clusterAccess;

    // Mode which is resolved, also when 'auto' is set
    private PlatformMode platformMode;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        clusterAccess = new ClusterAccess(namespace);

        // Platform mode is already used in executeInternal()
        super.execute();
    }

    @Override
    protected void executeInternal(ServiceHub hub) throws DockerAccessException, MojoExecutionException {
        if (project != null && skipBuildPom && Objects.equals("pom", project.getPackaging())) {
            getLog().debug("Disabling docker build for pom packaging");
            return;
        }
        super.executeInternal(hub);
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

        return GeneratorManager.generate(configs, extractGeneratorConfig(), project, log, platformMode, buildStrategy, useProjectClasspath);
    }

    @Override
    protected String getLogPrefix() {
        return "F8> ";
    }

    // ==================================================================================================

    // Get generator config
    private ProcessorConfig extractGeneratorConfig() {
        try {
            return ProfileUtil.extractProcesssorConfiguration(generator, ProfileUtil.GENERATOR_CONFIG, profile, resourceDir);
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
        String buildName = checkOrCreateBuildConfig(client, builder, imageConfig);
        checkOrCreateImageStream(client, builder, getImageStreamName(imageName));

        applyResourceObjects(client, builder);

        // Start the actual build
        startBuild(dockerTar, client, buildName);
    }

    private String getBuildName(ImageName imageName) {
        return imageName.getSimpleName();
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


    private void startBuild(File dockerTar, OpenShiftClient client, String buildName) {
        log.info("Starting Build %s",buildName);
        client.buildConfigs().withName(buildName)
              .instantiateBinary()
              .fromFile(dockerTar);
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
        ProcessorConfig resolvedEnricherConfig = ProfileUtil.extractProcesssorConfiguration(enricher, ProfileUtil.ENRICHER_CONFIG, profile, resourceDir);
        EnricherContext enricherContext = new EnricherContext(project, resolvedEnricherConfig, getResolvedImages(), resources, log, useProjectClasspath);
        EnricherManager enricherManager = new EnricherManager(enricherContext);
        enricherManager.enrich(builder);
    }

    //
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
            log.info("Using ImageStream %s", imageStreamName);
        }
    }

    private BuildRecreateMode getBuildRecreateMode() {
        return BuildRecreateMode.fromParameter(buildRecreate);
    }

    private String checkOrCreateBuildConfig(OpenShiftClient client, KubernetesListBuilder builder, ImageConfiguration imageConfig) {
        ImageName imageName = new ImageName(imageConfig.getName());
        String buildName = getBuildName(imageName);
        String imageStreamName = getImageStreamName(imageName);

        BuildConfig buildConfig = client.buildConfigs().withName(buildName).get();

        if (buildConfig != null) {
            if (!getBuildRecreateMode().isBuildConfig()) {
                String type = buildConfig.getSpec().getStrategy().getType();
                if (!buildStrategy.isSame(type)) {
                    client.buildConfigs().withName(buildName).edit()
                          .editSpec()
                          .withStrategy(createBuildStrategy(imageConfig))
                          .endSpec()
                          .done();
                    log.info("Editing BuildConfig %s for %s build", buildName, getStrategyLabel());
                } else {
                    log.info("Using BuildConfig %s for %s build", buildName, getStrategyLabel());
                }
                return buildName;
            } else {
                client.buildConfigs().withName(buildName).delete();
            }
        }

        // Create afresh
        log.info("Creating BuildConfig %s for %s build", buildName, getStrategyLabel());
        builder.addNewBuildConfigItem()
                     .withNewMetadata()
                       .withName(buildName)
                     .endMetadata()
                     .withNewSpec()
                       .withStrategy(createBuildStrategy(imageConfig))
                       .withNewSource()
                         .withType("Binary")
                       .endSource()
                       .withNewOutput()
                         .withNewTo()
                           .withKind("ImageStreamTag")
                           .withName(imageStreamName + ":" + imageName.getTag())
                         .endTo()
                       .endOutput()
                     .endSpec()
                   .endBuildConfigItem();
        return buildName;
    }

    private String getStrategyLabel() {
        return buildStrategy == OpenShiftBuildStrategy.s2i ? "S2I" : "Docker";
    }

    private BuildStrategy createBuildStrategy(ImageConfiguration imageConfig) {
        if (buildStrategy == OpenShiftBuildStrategy.docker) {
            return new BuildStrategyBuilder().withType("Docker").build();
        } else if (buildStrategy == OpenShiftBuildStrategy.s2i) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            String fromImage = buildConfig.getFrom();

            return new BuildStrategyBuilder()
                .withType("Source")
                .withNewSourceStrategy()
                  .withNewFrom()
                     .withKind("DockerImage")
                     .withName(fromImage)
                  .endFrom()
                .endSourceStrategy()
                .build();
        } else {
            throw new IllegalArgumentException("Unsupported BuildStrategy " + buildStrategy);
        }
    }

}
