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


import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.config.RuntimeMode;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.ProfileUtil;
import io.fabric8.maven.docker.AbstractDockerMojo;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.generator.api.GeneratorContext;
import io.fabric8.maven.plugin.generator.GeneratorManager;
import io.fabric8.maven.plugin.mojo.ResourceDirCreator;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.util.List;

import static io.fabric8.maven.core.service.kubernetes.jib.JibServiceUtil.jibPush;
import static io.fabric8.maven.core.util.Fabric8MavenPluginDeprecationUtil.logFabric8MavenPluginDeprecation;

/**
 * Uploads the built Docker images to a Docker registry
 *
 * @author roland
 * @since 16/03/16
 */
@Mojo(name = "push", defaultPhase = LifecyclePhase.INSTALL, requiresDependencyResolution = ResolutionScope.COMPILE)
public class PushMojo extends AbstractDockerMojo {

    /**
     * Generator specific options. This is a generic prefix where the keys have the form
     * <code>&lt;generator-prefix&gt;-&lt;option&gt;</code>.
     */
    @Parameter
    private ProcessorConfig generator;

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

    /**
     * Whether to perform a Kubernetes build (i.e. agains a vanilla Docker daemon) or
     * an OpenShift build (with a Docker build against the OpenShift API server.
     */
    @Parameter(property = "fabric8.mode")
    private RuntimeMode mode = RuntimeMode.auto;

    /**
     * OpenShift build mode when an OpenShift build is performed.
     * Can be either "s2i" for an s2i binary build mode or "docker" for a binary
     * docker mode.
     */
    @Parameter(property = "fabric8.build.strategy" )
    private OpenShiftBuildStrategy buildStrategy = OpenShiftBuildStrategy.s2i;

    @Parameter(property = "docker.source.dir", defaultValue="src/main/docker")
    private String sourceDirectory;

    @Parameter(property = "fabric8.build.jib", defaultValue = "false")
    private boolean isJib;

    @Parameter(property = "docker.push.retries", defaultValue = "0")
    private int retries;

    @Parameter(property = "fabric8.skip.push", defaultValue = "false")
    private boolean fabric8SkipPush;

    @Parameter(alias = "pushRegistry", property = "docker.push.registry")
    private String fabric8PushRegistry;

    @Parameter(alias = "outputDirectory", property = "docker.target.dir", defaultValue="target/docker")
    private String fabric8OutputDirectory;

    /**
     * Skip building tags
     */
    @Parameter(property = "docker.skip.tag", defaultValue = "false")
    private boolean skipTag;

    @Parameter(property = "fabric8.logDeprecationWarning", defaultValue = "true")
    protected boolean logDeprecationWarning;

    @Override
    protected String getLogPrefix() {
        return "F8> ";
    }

    protected boolean isJibMode() {
        return isJib || Configs.asBoolean(getProperty("fabric8.build.jib"));
    }

    @Override
    protected boolean isDockerAccessRequired() {
        return !isJibMode();
    }

    private String getProperty(String key) {
        String value = System.getProperty(key);
        if (value == null) {
            value = project.getProperties().getProperty(key);
        }
        return value;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip || fabric8SkipPush) {
            return;
        }

        logFabric8MavenPluginDeprecation(log, logDeprecationWarning);
        super.execute();
        logFabric8MavenPluginDeprecation(log, logDeprecationWarning);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void executeInternal(ServiceHub hub) throws DockerAccessException, MojoExecutionException {
        if (fabric8SkipPush) {
            return;
        }

        if (isJibMode()) {
            for (ImageConfiguration imageConfiguration : getResolvedImages()) {
                jibPush(imageConfiguration, project, getRegistryConfig(fabric8PushRegistry), fabric8OutputDirectory, log);
            }
        } else {
            hub.getRegistryService().pushImages(getResolvedImages(), retries, getRegistryConfig(fabric8PushRegistry), skipTag);
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
        try {
            ProcessorConfig generatorConfig =
                ProfileUtil.blendProfileWithConfiguration(ProfileUtil.GENERATOR_CONFIG, profile, ResourceDirCreator.getFinalResourceDir(resourceDir, environment), generator);
            GeneratorContext ctx = new GeneratorContext.Builder()
                .config(generatorConfig)
                .project(project)
                .logger(log)
                .runtimeMode(mode)
                .strategy(buildStrategy)
                .useProjectClasspath(false)
                .build();
            return GeneratorManager.generate(configs, ctx, true);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot extract generator config: " + e,e);
        }
    }

}
