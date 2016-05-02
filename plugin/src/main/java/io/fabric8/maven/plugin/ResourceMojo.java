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
import java.io.IOException;
import java.util.List;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.plugin.config.KubernetesConfiguration;
import io.fabric8.maven.plugin.util.ResourceFileType;
import io.fabric8.maven.plugin.util.EnricherManager;
import io.fabric8.maven.plugin.handler.HandlerHub;
import io.fabric8.maven.plugin.handler.ReplicationControllerHandler;
import io.fabric8.maven.plugin.handler.ServiceHandler;
import io.fabric8.maven.enricher.api.MavenBuildContext;
import io.fabric8.maven.plugin.util.KubernetesResourceUtil;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import static io.fabric8.kubernetes.api.KubernetesHelper.loadJson;
import static io.fabric8.maven.plugin.util.ResourceFileType.yaml;
import static io.fabric8.maven.plugin.util.KubernetesResourceUtil.writeResourceDescriptor;

/**
 * Generates or copies the Kubernetes JSON file and attaches it to the build so its
 * installed and released to maven repositories like other build artifacts.
 */
@Mojo(name = "resource", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class ResourceMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    /**
     * Folder where to find project specific files
     */
    @Parameter(property = "fabric8.resourceDir", defaultValue = "${basedir}/src/main/fabric8")
    private File resourceDir;

    /**
     * The artifact type for attaching the generated resource file to the project.
     * Can be either 'json' or 'yaml'
     */
    @Parameter(property = "fabric8.resourceType")
    private ResourceFileType resourceFileType = yaml;

    /**
     * The generated kubernetes JSON file
     */
    @Parameter(property = "fabric8.targetDir", defaultValue = "${project.build.outputDirectory}")
    private File target;

    /**
     * Whether to skip the execution of this plugin. Best used as property "fabric8.skip"
     */
    @Parameter(property = "fabric8.skip", defaultValue = "false")
    private boolean skip;

    // Kubernetes specific configuration for this plugin
    @Parameter
    private KubernetesConfiguration kubernetes;

    // Reusing image configuration from d-m-p
    @Parameter
    private List<ImageConfiguration> images;

    // Services
    private HandlerHub handlerHub;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            EnricherManager enricher = new EnricherManager(new MavenBuildContext(project));
            handlerHub = new HandlerHub(project, enricher);

            if (!skip && (!isPomProject() || hasFabric8Dir())) {
                KubernetesList resources = generateResourceDescriptor();
                writeResourceDescriptor(resources, new File(target,"fabric8"), resourceFileType);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate fabric8 descriptor", e);
        }
    }

    // ==================================================================================

    private KubernetesList generateResourceDescriptor() throws IOException {
        KubernetesListBuilder builder = hasFabric8Dir() ?
            KubernetesResourceUtil.readResourceFragmentsFrom("v1", resourceDir) :
            new KubernetesListBuilder();

        ServiceHandler serviceHandler = handlerHub.getServiceHandler();
        ReplicationControllerHandler rcHandler = handlerHub.getReplicationControllerHandler();

        builder.addToReplicationControllerItems(rcHandler.getReplicationControllers(kubernetes, images));
        builder.addToServiceItems(serviceHandler.getServices(kubernetes));
        return builder.build();
    }


    private boolean hasFabric8Dir() {
        return resourceDir.isDirectory();
    }

    private boolean isPomProject() {
        return "pom".equals(project.getPackaging());
    }

}
