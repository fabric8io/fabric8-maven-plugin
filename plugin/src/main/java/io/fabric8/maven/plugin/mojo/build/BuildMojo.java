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

import io.fabric8.maven.core.access.ClusterConfiguration;
import io.fabric8.maven.core.config.RuntimeMode;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.plugin.mojo.ResourceDirCreator;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import io.fabric8.maven.core.access.ClusterAccess;
import io.fabric8.maven.core.config.BuildRecreateMode;
import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.service.Fabric8ServiceHub;
import io.fabric8.maven.core.util.ProfileUtil;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import io.fabric8.maven.generator.api.GeneratorContext;
import io.fabric8.maven.plugin.enricher.EnricherManager;
import io.fabric8.maven.plugin.generator.GeneratorManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.repository.RepositorySystem;

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

    /**
     * Environment name where resources are placed. For example, if you set this property to dev and resourceDir is the default one, Fabric8 will look at src/main/fabric8/dev
     */
    @Parameter(property = "fabric8.environment")
    private String environment;

    @Parameter(property = "fabric8.skip.build.pom")
    private Boolean skipBuildPom;

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
    @Parameter(property = "fabric8.build.strategy" )
    private OpenShiftBuildStrategy buildStrategy = OpenShiftBuildStrategy.s2i;

    /**
     * The S2I binary builder BuildConfig name suffix appended to the image name to avoid
     * clashing with the underlying BuildConfig for the Jenkins pipeline
     */
    @Parameter(property = "fabric8.s2i.buildNameSuffix", defaultValue = "-s2i")
    private String s2iBuildNameSuffix;

    /**
     * The name of pullSecret to be used to pull the base image in case pulling from a private
     * registry which requires authentication.
     */
    @Parameter(property = "fabric8.build.pullSecret", defaultValue = "pullsecret-fabric8")
    private String openshiftPullSecret;

    /**
     * Allow the ImageStream used in the S2I binary build to be used in standard
     * Kubernetes resources such as Deployment or StatefulSet.
     */
    @Parameter(property = "fabric8.s2i.imageStreamLookupPolicyLocal", defaultValue = "true")
    private boolean s2iImageStreamLookupPolicyLocal = true;

    /**
     * While creating a BuildConfig, By default, if the builder image specified in the
     * build configuration is available locally on the node, that image will be used.
     *
     * ForcePull to override the local image and refresh it from the registry to which the image stream points.
     *
     */
    @Parameter(property = "fabric8.build.forcePull", defaultValue = "false")
    private boolean forcePull = false;

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

    @Parameter(property = "docker.skip.build", defaultValue = "false")
    protected boolean skipBuild;

    @Component
    private MavenProjectHelper projectHelper;

    @Component
    protected RepositorySystem repositorySystem;

    @Parameter
    protected ClusterConfiguration access;

    // Access for creating OpenShift binary builds
    private ClusterAccess clusterAccess;

    // The Fabric8 service hub
    Fabric8ServiceHub fabric8ServiceHub;

    // Mode which is resolved, also when 'auto' is set
    private RuntimeMode platformMode;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip || skipBuild) {
            return;
        }
        clusterAccess = new ClusterAccess(getClusterConfiguration());
        // Platform mode is already used in executeInternal()
        super.execute();
    }

    protected ClusterConfiguration getClusterConfiguration() {
        final ClusterConfiguration.Builder clusterConfigurationBuilder = new ClusterConfiguration.Builder(access);

        return clusterConfigurationBuilder.from(System.getProperties())
            .from(project.getProperties()).build();
    }

    @Override
    protected boolean isDockerAccessRequired() {
        return platformMode == RuntimeMode.kubernetes;
    }

    @Override
    protected void executeInternal(ServiceHub hub) throws MojoExecutionException {
        if (skipBuild) {
            return;
        }

        try {
            if (shouldSkipBecauseOfPomPackaging()) {
                getLog().info("Disabling docker build for pom packaging");
                return;
            }
            if (getResolvedImages().size() == 0) {
                log.warn("No image build configuration found or detected");
            }

            // Build the fabric8 service hub
            fabric8ServiceHub = new Fabric8ServiceHub.Builder()
                    .log(log)
                    .clusterAccess(clusterAccess)
                    .platformMode(mode)
                    .dockerServiceHub(hub)
                    .buildServiceConfig(getBuildServiceConfig())
                    .repositorySystem(repositorySystem)
                    .mavenProject(project)
                    .build();

            super.executeInternal(hub);

            fabric8ServiceHub.getBuildService().postProcess(getBuildServiceConfig());
        } catch(IOException e) {
            throw new MojoExecutionException(e.getMessage());
        }
    }

    private boolean shouldSkipBecauseOfPomPackaging() {
        if (!Objects.equals("pom", project.getPackaging())) {
            // No pom packaging
            return false;
        }
        if (skipBuildPom != null) {
            // If configured take the config option
            return skipBuildPom;
        }

        // Not specified: Skip if no image with build configured, otherwise don't skip
        for (ImageConfiguration image : getResolvedImages()) {
            if (image.getBuildConfiguration() != null) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void buildAndTag(ServiceHub hub, ImageConfiguration imageConfig)
        throws MojoExecutionException, DockerAccessException {

        try {
            // TODO need to refactor d-m-p to avoid this call
            EnvUtil.storeTimestamp(this.getBuildTimestampFile(), this.getBuildTimestamp());

            fabric8ServiceHub.getBuildService().build(imageConfig);

        } catch (Exception ex) {
            throw new MojoExecutionException("Failed to execute the build", ex);
        }
    }

    protected io.fabric8.maven.core.service.BuildService.BuildServiceConfig getBuildServiceConfig() throws MojoExecutionException {
        return new io.fabric8.maven.core.service.BuildService.BuildServiceConfig.Builder()
                .dockerBuildContext(getBuildContext())
                .dockerMojoParameters(createMojoParameters())
                .buildRecreateMode(BuildRecreateMode.fromParameter(buildRecreate))
                .openshiftBuildStrategy(buildStrategy)
                .openshiftPullSecret(openshiftPullSecret)
                .s2iBuildNameSuffix(s2iBuildNameSuffix)
                .s2iImageStreamLookupPolicyLocal(s2iImageStreamLookupPolicyLocal)
                .forcePullEnabled(forcePull)
                .imagePullManager(getImagePullManager(imagePullPolicy, autoPull))
                .buildDirectory(project.getBuild().getDirectory())
                .attacher((classifier, destFile) -> {
                    if (destFile.exists()) {
                        projectHelper.attachArtifact(project, "yml", classifier, destFile);
                    }
                })
                .enricherTask(builder ->
                                  new EnricherManager(resources, getEnricherContext(),
                                                      MavenUtil.getCompileClasspathElementsIfRequested(project, useProjectClasspath))
                                      .enrich(PlatformMode.openshift, builder))
                .build();
    }


    /**
     * Customization hook called by the base plugin.
     *
     * @param configs configuration to customize
     * @return the configuration customized by our generators.
     */
    @Override
    public List<ImageConfiguration> customizeConfig(List<ImageConfiguration> configs) {
        platformMode = clusterAccess.resolveRuntimeMode(mode, log);
        if (platformMode == RuntimeMode.openshift) {
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
            return GeneratorManager.generate(configs, getGeneratorContext(), false);
        } catch (MojoExecutionException e) {
            throw new IllegalArgumentException("Cannot extract generator config: " + e, e);
        }
    }

    @Override
    protected String getLogPrefix() {
        return "F8: ";
    }

    // ==================================================================================================

    // Get generator context
    private GeneratorContext getGeneratorContext() {
        return new GeneratorContext.Builder()
                .config(extractGeneratorConfig())
                .project(project)
                .logger(log)
                .platformMode(platformMode)
                .strategy(buildStrategy)
                .useProjectClasspath(useProjectClasspath)
                .artifactResolver(getFabric8ServiceHub().getArtifactResolverService())
                .build();
    }

    private Fabric8ServiceHub getFabric8ServiceHub() {
        return new Fabric8ServiceHub.Builder()
                .log(log)
                .clusterAccess(clusterAccess)
                .platformMode(mode)
                .repositorySystem(repositorySystem)
                .mavenProject(project)
                .build();
    }

    // Get generator config
    private ProcessorConfig extractGeneratorConfig() {
        try {
            return ProfileUtil.blendProfileWithConfiguration(ProfileUtil.GENERATOR_CONFIG, profile, ResourceDirCreator.getFinalResourceDir(resourceDir, environment), generator);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot extract generator config: " + e,e);
        }
    }

    // Get enricher context
    public EnricherContext getEnricherContext() {
        return new MavenEnricherContext.Builder()
                .project(project)
                .runtimeMode(mode)
                .session(session)
                .config(extractEnricherConfig())
                .images(getResolvedImages())
                .resources(resources)
                .log(log)
                .build();
    }

    // Get enricher config
    private ProcessorConfig extractEnricherConfig() {
        try {
            return ProfileUtil.blendProfileWithConfiguration(ProfileUtil.ENRICHER_CONFIG, profile, ResourceDirCreator.getFinalResourceDir(resourceDir, environment), enricher);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot extract enricher config: " + e,e);
        }
    }


}
