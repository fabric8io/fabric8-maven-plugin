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
import java.util.*;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ConfigHelper;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.handler.ImageConfigResolver;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import io.fabric8.maven.plugin.config.KubernetesConfiguration;
import io.fabric8.maven.plugin.config.ServiceConfiguration;
import io.fabric8.maven.plugin.config.ServiceProtocol;
import io.fabric8.maven.plugin.customizer.ImageConfigCustomizerManager;
import io.fabric8.maven.plugin.enricher.EnricherManager;
import io.fabric8.maven.plugin.handler.HandlerHub;
import io.fabric8.maven.plugin.handler.ReplicaSetHandler;
import io.fabric8.maven.plugin.handler.ServiceHandler;
import io.fabric8.maven.plugin.util.KubernetesResourceUtil;
import io.fabric8.maven.plugin.util.ResourceFileType;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.utils.StringUtils;

import static io.fabric8.maven.plugin.util.ResourceFileType.yaml;

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

    // Kubernetes specific configuration for this plugin
    @Parameter
    private KubernetesConfiguration kubernetes;

    // Reusing image configuration from d-m-p
    @Parameter
    private List<ImageConfiguration> images;

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
            EnricherManager enricher = new EnricherManager(new MavenEnricherContext(project));
            handlerHub = new HandlerHub(project);
            resolvedImages = resolveImages(images, log);

            if (!skip && (!isPomProject() || hasFabric8Dir())) {
                KubernetesList resources = generateResourceDescriptor(enricher, resolvedImages);
                KubernetesResourceUtil.writeResourceDescriptor(resources, new File(target, "fabric8"), resourceFileType);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate fabric8 descriptor", e);
        }
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
                    return ImageConfigCustomizerManager.customize(configs, project);
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

    private KubernetesList generateResourceDescriptor(final EnricherManager enricher, List<ImageConfiguration> images)
        throws IOException, MojoExecutionException {
        File[] resourceFiles = KubernetesResourceUtil.listResourceFragments(resourceDir);
        ReplicaSetHandler rsHandler = handlerHub.getReplicaSetHandler();
        ServiceHandler serviceHandler = handlerHub.getServiceHandler();

        KubernetesListBuilder builder;

        // Add resource files found in the fabric8 directory
        if (resourceFiles != null && resourceFiles.length > 0) {
            log.info("Using resource templates from %s", resourceDir);
            builder = KubernetesResourceUtil.readResourceFragmentsFrom("v1", filterFiles(resourceFiles));
        } else {
            builder = new KubernetesListBuilder();
        }

        // Add services + replicaSet if configured in plugin config
        if (kubernetes != null) {
            log.info("Adding resources from plugin configuration");
            addServices(builder, kubernetes.getServices(), kubernetes.getAnnotations().getService());
            builder.addToReplicaSetItems(rsHandler.getReplicaSet(kubernetes, images));
        }

        // Check if at least a replica set is added. If not add a default one
        if (!hasPodControllers(builder)) {
            String rcName = createDefaultReplicaSetName(project);
            log.info("Adding a default ReplicationController '%s'",rcName);
            builder.addToReplicaSetItems(rsHandler.getReplicaSet(
                new KubernetesConfiguration.Builder()
                    .replicaSetName(rcName)
                    .build(),
                resolvedImages));
        }

        // If no services are defined, add the exposed ports as services
        if (!hasServices(builder)) {
            builder.addToServiceItems(serviceHandler.getServices(
                extractDefaultServices(project, resolvedImages),
                // TODO: Annotations
                null));
        }

        // Enrich labels
        enricher.enrichLabels(builder);

        // Add missing selectors
        enricher.addMissingSelectors(builder);

        // Final customization hook
        enricher.customize(builder);

        return builder.build();
    }

    // Check for all build configs, extract the exposed ports and create a single service for all of them
    private List<ServiceConfiguration> extractDefaultServices(MavenProject project, List<ImageConfiguration> images) {
        String serviceName = createDefaultServiceName(project);
        List<ServiceConfiguration.Port> ports = extractPortsFromImageConfiguration(images);
        log.info("Adding a default Service '%s' with ports [%s]", serviceName, formatPortsAsList(ports));

        ServiceConfiguration.Builder ret = new ServiceConfiguration.Builder()
            .name(serviceName);
        if (ports.size() > 0) {
            ret.ports(ports);
        } else {
            ret.headless(true);
        }
        return Collections.singletonList(ret.build());
    }

    // Examine images for build configuration and extract all ports
    private List<ServiceConfiguration.Port> extractPortsFromImageConfiguration(List<ImageConfiguration> images) {
        List<ServiceConfiguration.Port> ret = new ArrayList<>();
        for (ImageConfiguration image : images) {
            BuildImageConfiguration buildConfig = image.getBuildConfiguration();
            if (buildConfig != null) {
                List<String> ports = buildConfig.getPorts();
                if (ports != null) {
                    for (String port : ports) {
                        /// Todo: Check IANA names (also in case port is not numeric)
                        int portI = Integer.parseInt(port);
                        ret.add(
                            new ServiceConfiguration.Port.Builder()
                                .protocol(ServiceProtocol.tcp) // TODO: default for the moment
                                .port(portI)
                                .targetPort(portI)
                                .build()
                               );
                    }
                }
            }
        }
        return ret;
    }

    // Create a default service name
    private String createDefaultServiceName(MavenProject project) {
        return createDefaultName(project,"svc");
    }

    // Create a default replica set name based on Maven coordinates
    private String createDefaultReplicaSetName(MavenProject project) {
        return createDefaultName(project,"rs");
    }

    private String createDefaultName(MavenProject project, String suffix) {
        return project.getArtifactId() + "-" + suffix;
    }

    private String formatPortsAsList(List<ServiceConfiguration.Port> ports)  {
        List<String> p = new ArrayList<>();
        for (ServiceConfiguration.Port port : ports) {
            p.add(Integer.toString(port.getTargetPort()));
        }
        return StringUtils.join(p.iterator(),",");
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

    private boolean hasPodControllers(KubernetesListBuilder builder) {
        return checkForKind(builder, "ReplicationController", "ReplicaSet");
    }

    private boolean hasServices(KubernetesListBuilder builder) {
        return checkForKind(builder, "Service");
    }

    private boolean checkForKind(KubernetesListBuilder builder, String ... kinds) {
        Set<String> kindSet = new HashSet<>(Arrays.asList(kinds));
        for (HasMetadata item : builder.getItems()) {
            if (kindSet.contains(item.getKind())) {
                return true;
            }
        }
        return false;
    }

    private void addServices(KubernetesListBuilder builder, List<ServiceConfiguration> serviceConfig, Map<String, String> annotations) {
        if (serviceConfig != null) {
            ServiceHandler serviceHandler = handlerHub.getServiceHandler();
            builder.addToServiceItems(serviceHandler.getServices(serviceConfig, annotations));
        }
    }


    private boolean hasFabric8Dir() {
        return resourceDir.isDirectory();
    }

    private boolean isPomProject() {
        return "pom".equals(project.getPackaging());
    }

}
