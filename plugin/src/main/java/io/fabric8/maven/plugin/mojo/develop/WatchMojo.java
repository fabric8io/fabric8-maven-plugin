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


import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Set;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.maven.core.access.ClusterAccess;
import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.service.Fabric8ServiceHub;
import io.fabric8.maven.core.util.GoalFinder;
import io.fabric8.maven.core.util.Gofabric8Util;
import io.fabric8.maven.core.util.KubernetesResourceUtil;
import io.fabric8.maven.core.util.ProfileUtil;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.BuildService;
import io.fabric8.maven.docker.service.DockerAccessFactory;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.service.WatchService;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.generator.api.GeneratorContext;
import io.fabric8.maven.plugin.generator.GeneratorManager;
import io.fabric8.maven.plugin.watcher.WatcherManager;
import io.fabric8.maven.watcher.api.WatcherContext;

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

    // Whether to use color
    @Parameter(property = "fabric8.useColor", defaultValue = "true")
    protected boolean useColor;

    // For verbose output
    @Parameter(property = "fabric8.verbose", defaultValue = "false")
    protected boolean verbose;

    // Used for determining which mojos are called during a run
    @Component
    protected GoalFinder goalFinder;

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

        clusterAccess = new ClusterAccess(namespace);
        kubernetes = clusterAccess.createDefaultClient(log);

        super.execute();
    }

    @Override
    protected synchronized void executeInternal(ServiceHub hub) throws DockerAccessException, MojoExecutionException {
        this.hub = hub;

        URL masterUrl = kubernetes.getMasterUrl();
        KubernetesResourceUtil.validateKubernetesMasterUrl(masterUrl);

        File manifest;
        boolean isOpenshift = KubernetesHelper.isOpenShift(kubernetes);
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
        BuildService.BuildContext buildContext = getBuildContext();
        WatchService.WatchContext watchContext = getWatchContext();


        return new WatcherContext.Builder()
                .serviceHub(hub)
                .buildContext(buildContext)
                .watchContext(watchContext)
                .config(extractWatcherConfig())
                .goalName("fabric8:watch")
                .logger(log)
                .newPodLogger(createLogger("[[C]][NEW][[C]] "))
                .oldPodLogger(createLogger("[[R]][OLD][[R]] "))
                .mode(mode)
                .project(project)
                .session(session)
                .strategy(buildStrategy)
                .useProjectClasspath(useProjectClasspath)
                .namespace(clusterAccess.getNamespace())
                .kubernetesClient(kubernetes)
                .fabric8ServiceHub(getFabric8ServiceHub())
                .build();
    }

    protected Fabric8ServiceHub getFabric8ServiceHub() {
        return new Fabric8ServiceHub.Builder()
                .log(log)
                .clusterAccess(clusterAccess)
                .dockerServiceHub(hub)
                .platformMode(mode)
                .repositorySystem(repositorySystem)
                .build();
    }

    @Override
    protected DockerAccessFactory.DockerAccessContext getDockerAccessContext() {
        return new DockerAccessFactory.DockerAccessContext.Builder(super.getDockerAccessContext())
                .dockerHostProviders(Gofabric8Util.extractDockerHostProvider(log))
                .build();
    }

    @Override
    public List<ImageConfiguration> customizeConfig(List<ImageConfiguration> configs) {
        try {
            GeneratorContext ctx = new GeneratorContext.Builder()
                    .config(extractGeneratorConfig())
                    .project(project)
                    .session(session)
                    .goalFinder(goalFinder)
                    .goalName("fabric8:watch")
                    .logger(log)
                    .mode(mode)
                    .strategy(buildStrategy)
                    .useProjectClasspath(useProjectClasspath)
                    .fabric8ServiceHub(getFabric8ServiceHub())
                    .build();
            return GeneratorManager.generate(configs, ctx, false);
        } catch (MojoExecutionException e) {
            throw new IllegalArgumentException("Cannot extract generator config: " + e, e);
        }
    }

    // Get watcher config
    private ProcessorConfig extractWatcherConfig() {
        try {
            return ProfileUtil.blendProfileWithConfiguration(ProfileUtil.WATCHER_CONFIG, profile, resourceDir, watcher);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot extract watcher config: " + e, e);
        }
    }

    // Get generator config
    private ProcessorConfig extractGeneratorConfig() {
        try {
            return ProfileUtil.blendProfileWithConfiguration(ProfileUtil.GENERATOR_CONFIG, profile, resourceDir, generator);
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
