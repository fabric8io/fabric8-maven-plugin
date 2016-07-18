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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.maven.core.access.KubernetesAccess;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.MojoParameters;
import io.fabric8.maven.plugin.generator.GeneratorManager;
import org.apache.maven.plugin.MojoExecutionException;
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
     * Same as the the <code>generator</code> option. Please use "generator" this
     * will be removed soon.
     */
    @Deprecated
    @Parameter
    Map<String, String> customizer;

    /**
     * Generator specific options. This is a generic prefix where the keys have the form
     * <code>&lt;generator-prefix&gt;-&lt;option&gt;</code>.
     */
    @Parameter
    Map<String, String> generator;

    @Parameter(property = "fabric8.build.skip.pom", defaultValue = "true")
    boolean disablePomPackaging;

    @Parameter(property = "fabric8.build.mode")
    PlatformMode buildMode;

    /**
     * Namespace to use when doing a Docker build against OpenShift
     */
    @Parameter(property = "fabric8.namespace")
    private String namespace;

    // Access for creating OpenShift binary builds
    private KubernetesAccess kubernetesAccess;

    @Override
    protected void executeInternal(ServiceHub hub) throws DockerAccessException, MojoExecutionException {

        if (project != null && disablePomPackaging
            && Objects.equals("pom", project.getPackaging())) {
            getLog().debug("Disabling docker build for pom packaging");
            return;
        }
        super.executeInternal(hub);
    }

    @Override
    protected void buildAndTag(ServiceHub hub, ImageConfiguration imageConfig)
        throws MojoExecutionException, DockerAccessException {

        if (buildMode == PlatformMode.kubernetes) {
            super.buildAndTag(hub, imageConfig);
        } else if (buildMode == PlatformMode.openshift) {
            executeOpenShiftBuild(hub, imageConfig);
        } else {
            throw new MojoExecutionException("Unknown platform mode " + buildMode);
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
        return GeneratorManager.generate(configs, generator != null ? generator : customizer, project, log);
    }

    @Override
    protected String getLogPrefix() {
        return "F8> ";
    }

    // ==================================================================================================

    // Docker build with a binary source strategy
    private void executeOpenShiftBuild(ServiceHub hub, ImageConfiguration imageConfig) throws MojoExecutionException {
        MojoParameters params = createMojoParameters();
        KubernetesAccess access = new KubernetesAccess(namespace);
        ImageName imageName = new ImageName(imageConfig.getName());

        File dockerTar = hub.getArchiveService().createDockerBuildArchive(imageConfig, params);

        KubernetesClient client = access.createKubernetesClient();
        if (!KubernetesHelper.isOpenShift(client)) {
            throw new MojoExecutionException(
                "Cannot create OpenShift Docker build with a non-OpenShift cluster at " + client.getMasterUrl());
        }

        KubernetesListBuilder builder = new KubernetesListBuilder();
        addBuildConfig(builder, imageName);
        addImageStream(builder, imageName);

        try {
            FileInputStream dockerTarIs = new FileInputStream(dockerTar);
            // TODO: Wait unti kubernetes-client support instantiateBinary()
            // PR is underway ....
            // client.lists().create(builder.build());

        } catch (FileNotFoundException e) {
            throw new MojoExecutionException("Internal: Cannot read " + dockerTar + ": " + e,e);
        }
    }

    private void addImageStream(KubernetesListBuilder builder, ImageName imageName) {
        builder.addNewImageStreamItem()
                  .withNewMetadata()
                     .withName(imageName.getNameWithoutTag())
                  .endMetadata()
               .endImageStreamItem();
    }

    private void addBuildConfig(KubernetesListBuilder builder, ImageName imageName) {
        builder.addNewBuildConfigItem()
                   .withNewMetadata()
                       .withName(getBuildName(imageName))
                   .endMetadata()
                   .withNewSpec()
                       .withNewSource()
                          .withNewBinary()
                          .endBinary()
                       .endSource()
                       .withNewStrategy()
                          .withNewDockerStrategy()
                          .endDockerStrategy()
                       .endStrategy()
                       .withNewOutput()
                          .withNewTo()
                            .withKind("ImageStreamTag")
                            .withName(imageName.getFullName())
                          .endTo()
                       .endOutput()
                   .endSpec()
                 .endBuildConfigItem();
    }

    private String getBuildName(ImageName imageName) {
        return
            (imageName.getUser() != null ? imageName.getUser() + "-"  : "")
            + imageName.getRepository() + "-build";
    }
}
