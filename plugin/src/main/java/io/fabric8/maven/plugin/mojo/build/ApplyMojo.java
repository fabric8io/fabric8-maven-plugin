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


import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.model.DoneableService;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPathBuilder;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressRuleValue;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressBackend;
import io.fabric8.kubernetes.api.model.extensions.IngressBuilder;
import io.fabric8.kubernetes.api.model.extensions.IngressList;
import io.fabric8.kubernetes.api.model.extensions.IngressRule;
import io.fabric8.kubernetes.api.model.extensions.IngressSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.maven.core.access.ClusterAccess;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.service.ApplyService;
import io.fabric8.maven.core.util.FileUtil;
import io.fabric8.maven.core.util.ResourceUtil;
import io.fabric8.maven.core.util.kubernetes.Fabric8Annotations;
import io.fabric8.maven.core.util.kubernetes.KubernetesClientUtil;
import io.fabric8.maven.core.util.kubernetes.KubernetesHelper;
import io.fabric8.maven.core.util.kubernetes.KubernetesResourceUtil;
import io.fabric8.maven.core.util.kubernetes.OpenshiftHelper;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.plugin.mojo.AbstractFabric8Mojo;
import io.fabric8.maven.plugin.mojo.ResourceDirCreator;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteList;
import io.fabric8.openshift.api.model.RouteSpec;
import io.fabric8.openshift.api.model.RouteTargetReference;
import io.fabric8.openshift.api.model.RouteTargetReferenceBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Base class for goals which deploy the generated artifacts into the Kubernetes cluster
 */
@Mojo(name = "apply", requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.INSTALL)
public class ApplyMojo extends AbstractFabric8Mojo {

    public static final String DEFAULT_KUBERNETES_MANIFEST = "${basedir}/target/classes/META-INF/fabric8/kubernetes.yml";
    public static final String DEFAULT_OPENSHIFT_MANIFEST = "${basedir}/target/classes/META-INF/fabric8/openshift.yml";

    /**
     * Should we fail the build if an apply fails?
     */
    @Parameter(property = "fabric8.deploy.failOnError", defaultValue = "true")
    protected boolean failOnError;

    /**
     * Should we update resources by deleting them first and then creating them again?
     */
    @Parameter(property = "fabric8.recreate", defaultValue = "false")
    protected boolean recreate;

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
     * Should we create new kubernetes resources?
     */
    @Parameter(property = "fabric8.deploy.create", defaultValue = "true")
    private boolean createNewResources;

    /**
     * Should we use rolling upgrades to apply changes?
     */
    @Parameter(property = "fabric8.rolling", defaultValue = "false")
    private boolean rollingUpgrades;

    /**
     * Should we fail if there is no kubernetes json
     */
    @Parameter(property = "fabric8.deploy.failOnNoKubernetesJson", defaultValue = "false")
    private boolean failOnNoKubernetesJson;

    /**
     * In services only mode we only process services so that those can be recursively created/updated first
     * before creating/updating any pods and replication controllers
     */
    @Parameter(property = "fabric8.deploy.servicesOnly", defaultValue = "false")
    private boolean servicesOnly;

    /**
     * Do we want to ignore services. This is particularly useful when in recreate mode
     * to let you easily recreate all the ReplicationControllers and Pods but leave any service
     * definitions alone to avoid changing the portalIP addresses and breaking existing pods using
     * the service.
     */
    @Parameter(property = "fabric8.deploy.ignoreServices", defaultValue = "false")
    private boolean ignoreServices;

    /**
     * Process templates locally in Java so that we can apply OpenShift templates on any Kubernetes environment
     */
    @Parameter(property = "fabric8.deploy.processTemplatesLocally", defaultValue = "false")
    private boolean processTemplatesLocally;

    /**
     * Should we delete all the pods if we update a Replication Controller
     */
    @Parameter(property = "fabric8.deploy.deletePods", defaultValue = "true")
    private boolean deletePodsOnReplicationControllerUpdate;

    /**
     * Do we want to ignore OAuthClients which are already running?. OAuthClients are shared across namespaces
     * so we should not try to update or create/delete global oauth clients
     */
    @Parameter(property = "fabric8.deploy.ignoreRunningOAuthClients", defaultValue = "true")
    private boolean ignoreRunningOAuthClients;

    /**
     * The folder we should store any temporary json files or results
     */
    @Parameter(property = "fabric8.deploy.jsonLogDir", defaultValue = "${basedir}/target/fabric8/applyJson")
    private File jsonLogDir;

    /**
     * How many seconds to wait for a URL to be generated for a service
     */
    @Parameter(property = "fabric8.serviceUrl.waitSeconds", defaultValue = "5")
    protected long serviceUrlWaitTimeSeconds;

    /**
     * The S2I binary builder BuildConfig name suffix appended to the image name to avoid
     * clashing with the underlying BuildConfig for the Jenkins pipeline
     */
    @Parameter(property = "fabric8.s2i.buildNameSuffix", defaultValue = "-s2i")
    protected String s2iBuildNameSuffix;

    @Parameter
    protected ResourceConfig resources;

    /**
     * Folder where to find project specific files
     */
    @Parameter(property = "fabric8.resourceDir", defaultValue = "${basedir}/src/main/fabric8")
    private File resourceDir;

    /**
     * Environment name where resources are placed. For example, if you set this property to dev and resourceDir is the default one, Fabric8 will look at src/main/fabric8/dev
     * Same applies for resourceDirOpenShiftOverride property.
     */
    @Parameter(property = "fabric8.environment")
    private String environment;

    @Parameter(property = "fabric8.skip.apply", defaultValue = "false")
    protected boolean skipApply;

    private ClusterAccess clusterAccess;

    protected ApplyService applyService;

    public void executeInternal() throws MojoExecutionException {
        if (skipApply) {
            return;
        }

        clusterAccess = new ClusterAccess(getClusterConfiguration());

        try {
            KubernetesClient kubernetes = clusterAccess.createDefaultClient(log);
            applyService = new ApplyService(kubernetes, log);
            initServices(kubernetes, log);

            URL masterUrl = kubernetes.getMasterUrl();
            File manifest;
            if (OpenshiftHelper.isOpenShift(kubernetes)) {
                manifest = openshiftManifest;
            } else {
                manifest = kubernetesManifest;
            }
            if (!manifest.exists() || !manifest.isFile()) {
                if (failOnNoKubernetesJson) {
                    throw new MojoFailureException("No such generated manifest file: " + manifest);
                } else {
                    log.warn("No such generated manifest file %s for this project so ignoring", manifest);
                    return;
                }
            }

            String clusterKind = "Kubernetes";
            if (OpenshiftHelper.isOpenShift(kubernetes)) {
                clusterKind = "OpenShift";
            }
            KubernetesResourceUtil.validateKubernetesMasterUrl(masterUrl);
            log.info("Using %s at %s in namespace %s with manifest %s ", clusterKind, masterUrl, clusterAccess.getNamespace(), manifest);

            applyService.setAllowCreate(createNewResources);
            applyService.setServicesOnlyMode(servicesOnly);
            applyService.setIgnoreServiceMode(ignoreServices);
            applyService.setLogJsonDir(jsonLogDir);
            applyService.setBasedir(getRootProjectFolder());
            applyService.setIgnoreRunningOAuthClients(ignoreRunningOAuthClients);
            applyService.setProcessTemplatesLocally(processTemplatesLocally);
            applyService.setDeletePodsOnReplicationControllerUpdate(deletePodsOnReplicationControllerUpdate);
            applyService.setRollingUpgrade(rollingUpgrades);
            applyService.setRollingUpgradePreserveScale(isRollingUpgradePreserveScale());

            boolean openShift = OpenshiftHelper.isOpenShift(kubernetes);
            if (openShift) {
                getLog().info("OpenShift platform detected");
            } else {
                disableOpenShiftFeatures(applyService);
            }

            Set<HasMetadata> entities = KubernetesResourceUtil.loadResources(manifest);

            String namespace = clusterAccess.getNamespace();
            boolean namespaceEntityExist = false;

            for (HasMetadata entity: entities) {
                if (entity instanceof Namespace) {
                    Namespace ns = (Namespace)entity;
                    namespace = ns.getMetadata().getName();
                    applyService.applyNamespace((ns));
                    namespaceEntityExist = true;
                    entities.remove(entity);
                    break;
                }
                if (entity instanceof Project) {
                    Project project = (Project)entity;
                    namespace = project.getMetadata().getName();
                    applyService.applyProject(project);
                    namespaceEntityExist = true;
                    entities.remove(entity);
                    break;
                }
            }

            if (!namespaceEntityExist) {
                applyService.applyNamespace(namespace);
            }

            applyService.setNamespace(namespace);

            applyEntities(kubernetes, namespace, manifest.getName(), entities);
            log.info("[[B]]HINT:[[B]] Use the command `%s get pods -w` to watch your pods start up", clusterAccess.isOpenShiftImageStream(log) ? "oc" : "kubectl");

        } catch (KubernetesClientException e) {
            KubernetesResourceUtil.handleKubernetesClientException(e, this.log);
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    protected void initServices(KubernetesClient kubernetes, Logger log) {

    }


    protected void applyEntities(KubernetesClient kubernetes, String namespace, String fileName, Set<HasMetadata> entities) throws Exception {
        // Apply all items
        for (HasMetadata entity : entities) {
            if (entity instanceof Pod) {
                Pod pod = (Pod) entity;
                applyService.applyPod(pod, fileName);
            } else if (entity instanceof Service) {
                Service service = (Service) entity;
                applyService.applyService(service, fileName);
            } else if (entity instanceof ReplicationController) {
                ReplicationController replicationController = (ReplicationController) entity;
                applyService.applyReplicationController(replicationController, fileName);
            } else if (entity != null) {
                applyService.apply(entity, fileName);
            }
        }

        Logger serviceLogger = createExternalProcessLogger("[[G]][SVC][[G]] ");
        long serviceUrlWaitTimeSeconds = this.serviceUrlWaitTimeSeconds;
        for (HasMetadata entity : entities) {
            if (entity instanceof Service) {
                Service service = (Service) entity;
                String name = KubernetesHelper.getName(service);
                Resource<Service, DoneableService> serviceResource = kubernetes.services().inNamespace(namespace).withName(name);
                String url = null;
                // lets wait a little while until there is a service URL in case the exposecontroller is running slow
                for (int i = 0; i < serviceUrlWaitTimeSeconds; i++) {
                    if (i > 0) {
                        Thread.sleep(1000);
                    }
                    Service s = serviceResource.get();
                    if (s != null) {
                        url = getExternalServiceURL(s);
                        if (StringUtils.isNotBlank(url)) {
                            break;
                        }
                    }
                    if (!isExposeService(service)) {
                        break;
                    }
                }

                // lets not wait for other services
                serviceUrlWaitTimeSeconds = 1;
                if (StringUtils.isNotBlank(url) && url.startsWith("http")) {
                    serviceLogger.info("" + name + ": " + url);
                }
            }
        }
        processCustomEntities(kubernetes, namespace, resources != null ? resources.getCrdContexts() : null, false);
    }

    protected String getExternalServiceURL(Service service) {
        return KubernetesHelper.getOrCreateAnnotations(service).get(Fabric8Annotations.SERVICE_EXPOSE_URL.value());
    }

    protected boolean isExposeService(Service service) {
        String expose = KubernetesHelper.getLabels(service).get("expose");
        return expose != null && expose.toLowerCase().equals("true");
    }

    public boolean isRollingUpgrades() {
        return rollingUpgrades;
    }

    public boolean isRollingUpgradePreserveScale() {
        return false;
    }

    public MavenProject getProject() {
        return project;
    }

    /**
     * Lets disable OpenShift-only features if we are not running on OpenShift
     */
    protected void disableOpenShiftFeatures(ApplyService applyService) {
        // TODO we could check if the Templates service is running and if so we could still support templates?
        this.processTemplatesLocally = true;
        applyService.setSupportOAuthClients(false);
        applyService.setProcessTemplatesLocally(true);
    }




    protected void processCustomEntities(KubernetesClient client, String namespace, List<String> customResourceDefinitions, boolean isDelete) throws Exception {
        if(customResourceDefinitions == null)
            return;

        List<CustomResourceDefinitionContext> crdContexts = KubernetesClientUtil.getCustomResourceDefinitionContext(client ,customResourceDefinitions);
        Map<File, String> fileToCrdMap = getCustomResourcesFileToNamemap();

        for(CustomResourceDefinitionContext customResourceDefinitionContext : crdContexts) {
            for(Map.Entry<File, String> entry : fileToCrdMap.entrySet()) {
                if(entry.getValue().equals(customResourceDefinitionContext.getGroup())) {
                    if(isDelete) {
                        applyService.deleteCustomResource(entry.getKey(), namespace, customResourceDefinitionContext);
                    } else {
                        applyService.applyCustomResource(entry.getKey(), namespace, customResourceDefinitionContext);
                    }
                }
            }
        }
    }

    protected Map<File, String> getCustomResourcesFileToNamemap() throws IOException {
        Map<File, String> fileToCrdGroupMap = new HashMap<>();
        File resourceDirFinal = ResourceDirCreator.getFinalResourceDir(resourceDir, environment);
        File[] resourceFiles = KubernetesResourceUtil.listResourceFragments(resourceDirFinal, resources != null ? resources.getRemotes() : null, log);

        for (File file : resourceFiles) {
            if (file.getName().endsWith("cr.yml") || file.getName().endsWith("cr.yaml")) {
                Map<String, Object> customResource = KubernetesClientUtil.doReadCustomResourceFile(file);
                String apiVersion = customResource.get("apiVersion").toString();
                if (apiVersion.contains("/")) {
                    fileToCrdGroupMap.put(file, apiVersion.split("/")[0]);
                }
            }
        }
        return fileToCrdGroupMap;
    }

    /**
     * Returns the root project folder
     */
    protected File getRootProjectFolder() {
        File answer = null;
        MavenProject project = getProject();
        while (project != null) {
            File basedir = project.getBasedir();
            if (basedir != null) {
                answer = basedir;
            }
            project = project.getParent();
        }
        return answer;
    }

    /**
     * Returns the root project folder
     */
    protected MavenProject getRootProject() {
        MavenProject project = getProject();
        while (project != null) {
            MavenProject parent = project.getParent();
            if (parent == null) {
                break;
            }
            project = parent;
        }
        return project;
    }

}
