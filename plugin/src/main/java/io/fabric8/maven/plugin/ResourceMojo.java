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

import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.maven.core.config.ResourceConfiguration;
import io.fabric8.maven.core.config.ResourceMode;
import io.fabric8.maven.core.config.ServiceConfiguration;
import io.fabric8.maven.core.handler.*;
import io.fabric8.maven.core.util.KubernetesResourceUtil;
import io.fabric8.maven.core.util.ResourceFileType;
import io.fabric8.maven.docker.config.ConfigHelper;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.handler.ImageConfigResolver;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.plugin.customizer.ImageConfigCustomizerManager;
import io.fabric8.maven.plugin.enricher.EnricherManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenFilteringException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.fabric8.maven.core.util.ResourceFileType.yaml;


/**
 * Generates or copies the Kubernetes JSON file and attaches it to the build so its
 * installed and released to maven repositories like other build artifacts.
 */
@Mojo(name = "resource", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class ResourceMojo extends AbstractMojo {

    @Component(role = MavenFileFilter.class, hint = "default")
    private MavenFileFilter mavenFileFilter;

    @Component
    private ImageConfigResolver imageConfigResolver;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

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
     * The fabric8 working directory
     */
    @Parameter(property = "fabric8.workDir", defaultValue = "${project.build.directory}/fabric8")
    private File workDir;

    /**
     * Whether to skip the execution of this plugin. Best used as property "fabric8.skip"
     */
    @Parameter(property = "fabric8.skip", defaultValue = "false")
    private boolean skip;


    /**
     * Operational mode how resources should be created.
     */
    @Parameter(property = "fabric8.mode")
    private ResourceMode mode = ResourceMode.kubernetes;

    // Resource  specific configuration for this plugin
    @Parameter
    private ResourceConfiguration resources;

    // Reusing image configuration from d-m-p
    @Parameter
    private List<ImageConfiguration> images;

    /**
     * Enricher specific configuration configuration given through
     * to the various enrichers.
     */
    @Parameter
    private Map<String, String> enricher;

    /**
     * Configuration passed to customizers
     */
    @Parameter
    private Map<String, String> customizer;

    // Whether to use replica sets or replication controller. Could be configurable
    // but for now leave it hidden.
    private boolean useReplicaSet = true;

    // The image configuration after resolving and customization
    private List<ImageConfiguration> resolvedImages;

    // Services
    private HandlerHub handlerHub;

    // Logger to use
    protected Logger log;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            log = new AnsiLogger(getLog(), getBooleanConfigProperty("useColor",true), getBooleanConfigProperty("verbose", false), "F8> ");
            handlerHub = new HandlerHub(project);

            // Resolve the Docker image build configuration
            resolvedImages = resolveImages(images, log);

            // Manager for calling enrichers.
            EnricherManager enricherManager = new EnricherManager(new EnricherContext(project, enricher, resolvedImages, resources, log));

            if (!skip && (!isPomProject() || hasFabric8Dir())) {
                // Generate resources
                KubernetesList resources = generateResourceDescriptor(enricherManager, resolvedImages);

                // Write to descriptor to
                KubernetesResourceUtil.writeResourceDescriptor(resources, getTargetFile(), resourceFileType);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate fabric8 descriptor", e);
        }
    }

    private File getTargetFile() {
        // "kubernetes.yml" or "openshift.yml"
        return new File(target, mode.getFileName());
    }

    private List<ImageConfiguration> resolveImages(List<ImageConfiguration> images, Logger log) {
        final Properties resolveProperties = project.getProperties();
        List<ImageConfiguration> ret = ConfigHelper.resolveImages(
            images,
            new ConfigHelper.Resolver() {
                @Override
                public List<ImageConfiguration> resolve(ImageConfiguration image) {
                    return imageConfigResolver.resolve(image, resolveProperties);
                }
            },
            null,  // no filter
            new ConfigHelper.Customizer() {
                @Override
                public List<ImageConfiguration> customizeConfig(List<ImageConfiguration> configs) {
                    return ImageConfigCustomizerManager.customize(configs, customizer, project);
                }
            });

        ConfigHelper.initAndValidate(ret, null, log);
        return ret;
    }

    // Resolve properties with both `docker` (as used in d-m-p) and `fabric8` prefix
    private boolean getBooleanConfigProperty(String key, boolean defaultVal) {
        Properties props = System.getProperties();
        for (String prefix : new String[] { "fabric8", "docker"}) {
            String lookup = prefix + "." + key;
            if (props.containsKey(lookup)) {
                return Boolean.parseBoolean(lookup);
            }
        }
        return defaultVal;
    }

    // ==================================================================================

    private KubernetesList generateResourceDescriptor(final EnricherManager enricherManager, List<ImageConfiguration> images)
        throws IOException, MojoExecutionException {
        File[] resourceFiles = KubernetesResourceUtil.listResourceFragments(resourceDir);
        ReplicationControllerHandler rcHandler = handlerHub.getReplicationControllerHandler();

        KubernetesListBuilder builder;

        // Add resource files found in the fabric8 directory
        if (resourceFiles != null && resourceFiles.length > 0) {
            log.info("Using resource templates from %s", resourceDir);
            builder = KubernetesResourceUtil.readResourceFragmentsFrom("v1", filterFiles(resourceFiles));
        } else {
            builder = new KubernetesListBuilder();
        }

        // Add services + replicaSet if configured in plugin config
        if (resources != null) {
            log.info("Adding resources from plugin configuration");
            addServices(builder, resources.getServices(), resources.getAnnotations().getService());
            // TODO: Change to ReplicaSet ...
            builder.addToReplicationControllerItems(rcHandler.getReplicationController(resources, images));
        }

        // Enrich labels
        enricherManager.enrichLabels(builder);

        // Add missing selectors
        enricherManager.addMissingSelectors(builder);

        // Final customization hook
        enricherManager.customize(builder);

        return builder.build();
    }


    private File[] filterFiles(File[] resourceFiles) throws MojoExecutionException {
        if (!workDir.exists()) {
            if (!workDir.mkdirs()) {
                throw new MojoExecutionException("Cannot create working dir " + workDir);
            }
        }
        File[] ret = new File[resourceFiles.length];
        int i = 0;
        for (File resource : resourceFiles) {
            File targetFile = new File(workDir, resource.getName());
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


    private void addServices(KubernetesListBuilder builder, List<ServiceConfiguration> serviceConfig, Map<String, String> annotations) {
        if (serviceConfig != null) {
            ServiceHandler serviceHandler = handlerHub.getServiceHandler();
            builder.addToServiceItems(toArray(serviceHandler.getServices(serviceConfig, annotations)));
        }
    }

    // convert list to array, never returns null.
    private Service[] toArray(List<Service> services) {
        if (services == null) {
            return new Service[0];
        }
        if (services instanceof ArrayList) {
            return (Service[]) ((ArrayList) services).toArray(new Service[services.size()]);
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
