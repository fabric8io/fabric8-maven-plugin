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
package io.fabric8.maven.plugin.mojo.develop;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.maven.core.access.ClusterAccess;
import io.fabric8.maven.core.access.ClusterConfiguration;
import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.RuntimeMode;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.service.Fabric8ServiceHub;
import io.fabric8.maven.core.util.ProfileUtil;
import io.fabric8.maven.core.util.kubernetes.KubernetesResourceUtil;
import io.fabric8.maven.core.util.kubernetes.OpenshiftHelper;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.BuildService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.service.WatchService;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.generator.api.GeneratorContext;
import io.fabric8.maven.generator.api.GeneratorMode;
import io.fabric8.maven.plugin.generator.GeneratorManager;
import io.fabric8.maven.plugin.mojo.ResourceDirCreator;
import io.fabric8.maven.plugin.watcher.WatcherManager;
import io.fabric8.maven.watcher.api.WatcherContext;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Set;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.repository.RepositorySystem;

import static io.fabric8.maven.plugin.mojo.build.ApplyMojo.DEFAULT_KUBERNETES_MANIFEST;
import static io.fabric8.maven.plugin.mojo.build.ApplyMojo.DEFAULT_OPENSHIFT_MANIFEST;


// TODO: Similar to the DebugMojo the WatchMojo should scale down any deployment to 1 replica (or ensure that its running only with one replica)
// The WatchEnricher has been removed since the enrichment shouldn't know anything about the mode running and should
// always create the same resources

/**
 * Used to automatically rebuild Docker images and restart containers in case of updates.
 */
@Mojo(name = "watch", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.COMPILE)
@Execute(goal = "deploy")
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
     * Whether to perform a Kubernetes build (i.e. against a vanilla Docker daemon) or
     * an OpenShift build (with a Docker build against the OpenShift API server.
     */
    @Parameter(property = "fabric8.mode")
    private RuntimeMode mode = RuntimeMode.auto;
    /**
     * OpenShift build mode when an OpenShift build is performed.
     * Can be either "s2i" for an s2i binary build mode or "docker" for a binary
     * docker mode.
     */
    @Parameter(property = "fabric8.build.strategy")
    private OpenShiftBuildStrategy buildStrategy = OpenShiftBuildStrategy.s2i;

    @Parameter
    protected ClusterConfiguration access;

    /**
     * Watcher specific options. This is a generic prefix where the keys have the form
     * <code>&lt;watcher-prefix&gt;-&lt;option&gt;</code>.
     */
    @Parameter
    private ProcessorConfig watcher;

    /**
     * Should we use the project's compile-time classpath to scan for additional enrichers/generators?
     */
    @Parameter(property = "fabric8.useProjectClasspath", defaultValue = "false")
    private boolean useProjectClasspath = false;

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

    // Whether to use color
    @Parameter(property = "fabric8.useColor", defaultValue = "true")
    protected boolean useColor;

    // For verbose output
    @Parameter(property = "fabric8.verbose", defaultValue = "false")
    protected boolean verbose;

    @Component
    protected RepositorySystem repositorySystem;

    private ClusterAccess clusterAccess;
    private KubernetesClient kubernetes;
    private ServiceHub hub;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            return;
        }

        clusterAccess = new ClusterAccess(getClusterConfiguration());
        kubernetes = clusterAccess.createDefaultClient(log);

        super.execute();
    }

    protected ClusterConfiguration getClusterConfiguration() {
        if(access == null) {
            access = new ClusterConfiguration.Builder().build();
        }
        final ClusterConfiguration.Builder clusterConfigurationBuilder = new ClusterConfiguration.Builder(access);

        return clusterConfigurationBuilder.from(System.getProperties())
            .from(project.getProperties()).build();
    }

    @Override
    protected synchronized void executeInternal(ServiceHub hub) throws MojoExecutionException {
        this.hub = hub;

        URL masterUrl = kubernetes.getMasterUrl();
        KubernetesResourceUtil.validateKubernetesMasterUrl(masterUrl);

        File manifest;
        boolean isOpenshift = OpenshiftHelper.isOpenShift(kubernetes);
        if (isOpenshift) {
            manifest = openshiftManifest;
        } else {
            manifest = kubernetesManifest;
        }

        try {
            Set<HasMetadata> resources = KubernetesResourceUtil.loadResources(manifest);
            WatcherContext context = getWatcherContext();

            WatcherManager.watch(getResolvedImages(), resources, context);

        } catch (KubernetesClientException ex) {
            KubernetesResourceUtil.handleKubernetesClientException(ex, this.log);
        } catch (Exception ex) {
            throw new MojoExecutionException("An error has occurred while while trying to watch the resources", ex);
        }

    }

    public WatcherContext getWatcherContext() throws MojoExecutionException {
        try {
            BuildService.BuildContext buildContext = getBuildContext();
            WatchService.WatchContext watchContext = getWatchContext(hub);


            return new WatcherContext.Builder()
                    .serviceHub(hub)
                    .buildContext(buildContext)
                    .watchContext(watchContext)
                    .config(extractWatcherConfig())
                    .logger(log)
                    .newPodLogger(createLogger("[[C]][NEW][[C]] "))
                    .oldPodLogger(createLogger("[[R]][OLD][[R]] "))
                    .mode(mode)
                    .project(project)

                    .useProjectClasspath(useProjectClasspath)
                    .clusterConfiguration(getClusterConfiguration())
                    .kubernetesClient(kubernetes)
                    .fabric8ServiceHub(getFabric8ServiceHub())
                    .build();
        } catch(IOException exception) {
            throw new MojoExecutionException(exception.getMessage());
        }
    }

    protected Fabric8ServiceHub getFabric8ServiceHub() {
        return new Fabric8ServiceHub.Builder()
                .log(log)
                .clusterAccess(clusterAccess)
                .dockerServiceHub(hub)
                .platformMode(mode)
                .repositorySystem(repositorySystem)
                .mavenProject(project)
                .build();
    }

    @Override
    public List<ImageConfiguration> customizeConfig(List<ImageConfiguration> configs) {
        try {
            Fabric8ServiceHub serviceHub = getFabric8ServiceHub();
            GeneratorContext ctx = new GeneratorContext.Builder()
                    .config(extractGeneratorConfig())
                    .project(project)
                    .logger(log)
                    .runtimeMode(mode)
                    .strategy(buildStrategy)
                    .useProjectClasspath(useProjectClasspath)
                    .artifactResolver(serviceHub.getArtifactResolverService())
                    .generatorMode(GeneratorMode.WATCH)
                    .build();
            return GeneratorManager.generate(configs, ctx, false);
        } catch (MojoExecutionException e) {
            throw new IllegalArgumentException("Cannot extract generator config: " + e, e);
        }
    }

    // Get watcher config
    private ProcessorConfig extractWatcherConfig() {
        try {
            return ProfileUtil.blendProfileWithConfiguration(ProfileUtil.WATCHER_CONFIG, profile, ResourceDirCreator.getFinalResourceDir(resourceDir, environment), watcher);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot extract watcher config: " + e, e);
        }
    }

    // Get generator config
    private ProcessorConfig extractGeneratorConfig() {
        try {
            return ProfileUtil.blendProfileWithConfiguration(ProfileUtil.GENERATOR_CONFIG, profile, ResourceDirCreator.getFinalResourceDir(resourceDir, environment), generator);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot extract generator config: " + e, e);
        }
    }

    protected Logger createLogger(String prefix) {
        return new AnsiLogger(getLog(), useColor, verbose, !settings.getInteractiveMode(), "F8:" + prefix);
    }

    @Override
    protected String getLogPrefix() {
        return "F8: ";
    }
}
