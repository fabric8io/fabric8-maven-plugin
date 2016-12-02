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
package io.fabric8.maven.plugin.mojo.build;


import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.Annotations;
import io.fabric8.kubernetes.api.Controller;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.extensions.Templates;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.DoneableService;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerSpec;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpec;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPathBuilder;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressRuleValue;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressBackend;
import io.fabric8.kubernetes.api.model.extensions.IngressBuilder;
import io.fabric8.kubernetes.api.model.extensions.IngressList;
import io.fabric8.kubernetes.api.model.extensions.IngressRule;
import io.fabric8.kubernetes.api.model.extensions.IngressSpec;
import io.fabric8.kubernetes.api.model.extensions.LabelSelector;
import io.fabric8.kubernetes.api.model.extensions.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.extensions.LabelSelectorRequirement;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.ClientNonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.ClientPodResource;
import io.fabric8.kubernetes.client.dsl.ClientResource;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.Scaleable;
import io.fabric8.kubernetes.internal.HasMetadataComparator;
import io.fabric8.maven.core.access.ClusterAccess;
import io.fabric8.maven.core.util.KubernetesResourceUtil;
import io.fabric8.maven.core.util.ProcessUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.plugin.mojo.AbstractFabric8Mojo;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigSpec;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteList;
import io.fabric8.openshift.api.model.RouteSpec;
import io.fabric8.openshift.api.model.RouteTargetReference;
import io.fabric8.openshift.api.model.RouteTargetReferenceBuilder;
import io.fabric8.openshift.api.model.Template;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.utils.Files;
import io.fabric8.utils.Strings;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static io.fabric8.kubernetes.api.KubernetesHelper.createIntOrString;
import static io.fabric8.kubernetes.api.KubernetesHelper.getKind;
import static io.fabric8.kubernetes.api.KubernetesHelper.getLabels;
import static io.fabric8.kubernetes.api.KubernetesHelper.getName;
import static io.fabric8.kubernetes.api.KubernetesHelper.getOrCreateAnnotations;

/**
 * Base class for goals which deploy the generated artifacts into the Kubernetes cluster
 */
@Mojo(name = "apply", requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.INSTALL)
public class ApplyMojo extends AbstractFabric8Mojo {

    public static final String DEFAULT_KUBERNETES_MANIFEST = "${basedir}/target/classes/META-INF/fabric8/kubernetes.yml";
    public static final String DEFAULT_OPENSHIFT_MANIFEST = "${basedir}/target/classes/META-INF/fabric8/openshift.yml";

    /**
     * The domain added to the service ID when creating OpenShift routes
     */
    @Parameter(property = "fabric8.domain")
    protected String routeDomain;

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
     * Should we create external Ingress/Routes for any LoadBalancer Services which don't already have them.
     * <p>
     * We now do not do this by default and defer this to the
     * <a href="https://github.com/fabric8io/exposecontroller/">exposecontroller</a> to decide
     * if Ingress or Router is being used or whether we should use LoadBalancer or NodePorts for single node clusters
     */
    @Parameter(property = "fabric8.deploy.createExternalUrls", defaultValue = "false")
    private boolean createExternalUrls;

    /**
     * The folder we should store any temporary json files or results
     */
    @Parameter(property = "fabric8.deploy.jsonLogDir", defaultValue = "${basedir}/target/fabric8/applyJson")
    private File jsonLogDir;

    /**
     * Namespace on which to operate
     */
    @Parameter(property = "fabric8.namespace")
    private String namespace;

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
    private String s2iBuildNameSuffix;

    private ClusterAccess clusterAccess;

    public static Route createRouteForService(String routeDomainPostfix, String namespace, Service service, Log log) {
        Route route = null;
        String id = KubernetesHelper.getName(service);
        if (Strings.isNotBlank(id) && shouldCreateExternalURLForService(log, service, id)) {
            route = new Route();
            String routeId = id;
            KubernetesHelper.setName(route, namespace, routeId);
            RouteSpec routeSpec = new RouteSpec();
            RouteTargetReference objectRef = new RouteTargetReferenceBuilder().withName(id).build();
            //objectRef.setNamespace(namespace);
            routeSpec.setTo(objectRef);
            if (!Strings.isNullOrBlank(routeDomainPostfix)) {
                String host = Strings.stripSuffix(Strings.stripSuffix(id, "-service"), ".");
                routeSpec.setHost(host + "." + Strings.stripPrefix(routeDomainPostfix, "."));
            } else {
                routeSpec.setHost("");
            }
            route.setSpec(routeSpec);
            String json;
            try {
                json = KubernetesHelper.toJson(route);
            } catch (JsonProcessingException e) {
                json = e.getMessage() + ". object: " + route;
            }
            log.debug("Created route: " + json);
        }
        return route;
    }

    public static Ingress createIngressForService(String routeDomainPostfix, String namespace, Service service, Log log) {
        Ingress ingress = null;
        String serviceName = KubernetesHelper.getName(service);
        ServiceSpec serviceSpec = service.getSpec();
        if (serviceSpec != null && Strings.isNotBlank(serviceName) &&
                shouldCreateExternalURLForService(log, service, serviceName)) {
            String ingressId = serviceName;
            String host = "";
            if (Strings.isNotBlank(routeDomainPostfix)) {
                host = serviceName + "." + namespace + "." + Strings.stripPrefix(routeDomainPostfix, ".");
            }
            List<HTTPIngressPath> paths = new ArrayList<>();
            List<ServicePort> ports = serviceSpec.getPorts();
            if (ports != null) {
                for (ServicePort port : ports) {
                    Integer portNumber = port.getPort();
                    if (portNumber != null) {
                        HTTPIngressPath path = new HTTPIngressPathBuilder().withNewBackend().
                                withServiceName(serviceName).withServicePort(createIntOrString(portNumber.intValue())).
                                endBackend().build();
                        paths.add(path);
                    }
                }
            }
            if (paths.isEmpty()) {
                return ingress;
            }
            ingress = new IngressBuilder().
                    withNewMetadata().withName(ingressId).withNamespace(namespace).endMetadata().
                    withNewSpec().
                    addNewRule().
                    withHost(host).
                    withNewHttp().
                    withPaths(paths).
                    endHttp().
                    endRule().
                    endSpec().build();

            String json;
            try {
                json = KubernetesHelper.toJson(ingress);
            } catch (JsonProcessingException e) {
                json = e.getMessage() + ". object: " + ingress;
            }
            log.debug("Created ingress: " + json);
        }
        return ingress;
    }

    /**
     * Should we try to create an external URL for the given service?
     * <p/>
     * By default lets ignore the kubernetes services and any service which does not expose ports 80 and 443
     *
     * @return true if we should create an OpenShift Route for this service.
     */
    protected static boolean shouldCreateExternalURLForService(Log log, Service service, String id) {
        if ("kubernetes".equals(id) || "kubernetes-ro".equals(id)) {
            return false;
        }
        Set<Integer> ports = KubernetesHelper.getPorts(service);
        log.debug("Service " + id + " has ports: " + ports);
        if (ports.size() == 1) {
            String type = null;
            ServiceSpec spec = service.getSpec();
            if (spec != null) {
                type = spec.getType();
                if (Objects.equals(type, "LoadBalancer")) {
                    return true;
                }
            }
            log.info("Not generating route for service " + id + " type is not LoadBalancer: " + type);
            return false;
        } else {
            log.info("Not generating route for service " + id + " as only single port services are supported. Has ports: " + ports);
            return false;
        }
    }

    public void executeInternal() throws MojoExecutionException, MojoFailureException {
        clusterAccess = new ClusterAccess(namespace);

        try {
            KubernetesClient kubernetes = clusterAccess.createDefaultClient(log);
            URL masterUrl = kubernetes.getMasterUrl();
            File manifest;
            if (KubernetesHelper.isOpenShift(kubernetes)) {
                manifest = openshiftManifest;
            } else {
                manifest = kubernetesManifest;
            }
            if (!Files.isFile(manifest)) {
                if (failOnNoKubernetesJson) {
                    throw new MojoFailureException("No such generated manifest file: " + manifest);
                } else {
                    log.warn("No such generated manifest file %s for this project so ignoring", manifest);
                    return;
                }
            }

            String clusterKind = "Kubernetes";
            if (KubernetesHelper.isOpenShift(kubernetes)) {
                clusterKind = "OpenShift";
            }
            KubernetesResourceUtil.validateKubernetesMasterUrl(masterUrl);
            log.info("Using %s at %s in namespace %s with manifest %s ", clusterKind, masterUrl, clusterAccess.getNamespace(), manifest);

            Controller controller = createController();
            controller.setAllowCreate(createNewResources);
            controller.setServicesOnlyMode(servicesOnly);
            controller.setIgnoreServiceMode(ignoreServices);
            controller.setLogJsonDir(jsonLogDir);
            controller.setBasedir(getRootProjectFolder());
            controller.setIgnoreRunningOAuthClients(ignoreRunningOAuthClients);
            controller.setProcessTemplatesLocally(processTemplatesLocally);
            controller.setDeletePodsOnReplicationControllerUpdate(deletePodsOnReplicationControllerUpdate);
            controller.setRollingUpgrade(rollingUpgrades);
            controller.setRollingUpgradePreserveScale(isRollingUpgradePreserveScale());

            boolean openShift = KubernetesHelper.isOpenShift(kubernetes);
            if (openShift) {
                getLog().info("OpenShift platform detected");
            } else {
                disableOpenShiftFeatures(controller);
            }

            // lets check we have created the namespace
            String namespace = clusterAccess.getNamespace();
            controller.applyNamespace(namespace);
            controller.setNamespace(namespace);

            Set<HasMetadata> entities = loadResources(kubernetes, controller, namespace, manifest, getProject(), log);

            if (createExternalUrls) {
                if (controller.getOpenShiftClientOrNull() != null) {
                    createRoutes(controller, entities);
                } else {
                    createIngress(controller, kubernetes, entities);
                }
            }
            applyEntities(controller, kubernetes, namespace, manifest.getName(), entities);

        } catch (KubernetesClientException e) {
            KubernetesResourceUtil.handleKubernetesClientException(e, this.log);
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    // TODO: Move it out into an utility class
    public static Set<HasMetadata> loadResources(KubernetesClient kubernetes, Controller controller, String namespace, File manifest, MavenProject project, Logger log) throws Exception {
        String fileName = manifest.getName();
        Object dto = KubernetesHelper.loadYaml(manifest, KubernetesResource.class);
        if (dto == null) {
            throw new MojoFailureException("Cannot load kubernetes YAML: " + manifest);
        }

        if (dto instanceof Template) {
            Template template = (Template) dto;
            boolean failOnMissingParameterValue = false;
            dto = Templates.processTemplatesLocally(template, failOnMissingParameterValue);
        }

        Set<KubernetesResource> resources = new LinkedHashSet<>();

        Set<HasMetadata> entities = new TreeSet<>(new HasMetadataComparator());
        for (KubernetesResource resource : resources) {
            entities.addAll(KubernetesHelper.toItemList(resource));
        }

        entities.addAll(KubernetesHelper.toItemList(dto));
        return entities;
    }

    protected void applyEntities(Controller controller, KubernetesClient kubernetes, String namespace, String fileName, Set<HasMetadata> entities) throws Exception {
        // Apply all items
        for (HasMetadata entity : entities) {
            if (entity instanceof Pod) {
                Pod pod = (Pod) entity;
                controller.applyPod(pod, fileName);
            } else if (entity instanceof Service) {
                Service service = (Service) entity;
                controller.applyService(service, fileName);
            } else if (entity instanceof ReplicationController) {
                ReplicationController replicationController = (ReplicationController) entity;
                controller.applyReplicationController(replicationController, fileName);
            } else if (entity != null) {
                controller.apply(entity, fileName);
            }
        }

        File file = null;
        try {
            file = getKubeCtlExecutable(controller);
        } catch (MojoExecutionException e) {
            log.warn("%s", e.getMessage());
        }
        if (file != null) {
            log.info("[[B]]HINT:[[B]] Use the command `%s get pods -w` to watch your pods start up",file.getName());
        }

        Logger serviceLogger = createExternalProcessLogger("[[G]][SVC][[G]] ");
        long serviceUrlWaitTimeSeconds = this.serviceUrlWaitTimeSeconds;
        for (HasMetadata entity : entities) {
            if (entity instanceof Service) {
                Service service = (Service) entity;
                String name = getName(service);
                ClientResource<Service, DoneableService> serviceResource = kubernetes.services().inNamespace(namespace).withName(name);
                String url = null;
                // lets wait a little while until there is a service URL in case the exposecontroller is running slow
                for (int i = 0; i < serviceUrlWaitTimeSeconds; i++) {
                    if (i > 0) {
                        Thread.sleep(1000);
                    }
                    Service s = serviceResource.get();
                    if (s != null) {
                        url = getExternalServiceURL(s);
                        if (Strings.isNotBlank(url)) {
                            break;
                        }
                    }
                    if (!isExposeService(service)) {
                        break;
                    }
                }

                // lets not wait for other services
                serviceUrlWaitTimeSeconds = 1;
                if (Strings.isNotBlank(url) && url.startsWith("http")) {
                    serviceLogger.info("" + name + ": " + url);
                }
            }
        }
    }

    protected String getExternalServiceURL(Service service) {
        return getOrCreateAnnotations(service).get(Annotations.Service.EXPOSE_URL);
    }

    protected boolean isExposeService(Service service) {
        String expose = getLabels(service).get("expose");
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
    protected void disableOpenShiftFeatures(Controller controller) {
        // TODO we could check if the Templates service is running and if so we could still support templates?
        this.processTemplatesLocally = true;
        controller.setSupportOAuthClients(false);
        controller.setProcessTemplatesLocally(true);
    }

    protected static Object applyTemplates(Template template, KubernetesClient kubernetes, Controller controller, String namespace, String fileName, MavenProject project, Logger log) throws Exception {
        KubernetesHelper.setNamespace(template, namespace);
        overrideTemplateParameters(template, project, log);
        return controller.applyTemplate(template, fileName);
    }

    /**
     * Before applying the given template lets allow template parameters to be overridden via the maven
     * properties - or optionally - via the command line if in interactive mode.
     */
    protected static void overrideTemplateParameters(Template template, MavenProject project, Logger log) {
        List<io.fabric8.openshift.api.model.Parameter> parameters = template.getParameters();
        if (parameters != null && project != null) {
            Properties properties = getProjectAndFabric8Properties(project);
            boolean missingProperty = false;
            for (io.fabric8.openshift.api.model.Parameter parameter : parameters) {
                String parameterName = parameter.getName();
                String name = "fabric8.apply." + parameterName;
                String propertyValue = properties.getProperty(name);
                if (propertyValue != null) {
                    log.info("Overriding template parameter " + name + " with value: " + propertyValue);
                    parameter.setValue(propertyValue);
                } else {
                    missingProperty = true;
                    log.info("No property defined for template parameter: " + name);
                }
            }
            if (missingProperty) {
                log.debug("Current properties " + new TreeSet<>(properties.keySet()));
            }
        }
    }

    protected static Properties getProjectAndFabric8Properties(MavenProject project) {
        Properties properties = project.getProperties();
        properties.putAll(project.getProperties());
        // let system properties override so we can read from the command line
        properties.putAll(System.getProperties());
        return properties;
    }

    protected void createRoutes(Controller controller, Collection<HasMetadata> collection) {
        String routeDomainPostfix = this.routeDomain;
        Log log = getLog();
        String namespace = clusterAccess.getNamespace();
        // lets get the routes first to see if we should bother
        try {
            OpenShiftClient openshiftClient = controller.getOpenShiftClientOrNull();
            if (openshiftClient == null) {
                return;
            }
            RouteList routes = openshiftClient.routes().inNamespace(namespace).list();
            if (routes != null) {
                routes.getItems();
            }
        } catch (Exception e) {
            log.warn("Cannot load OpenShift Routes; maybe not connected to an OpenShift platform? " + e, e);
            return;
        }
        List<Route> routes = new ArrayList<>();
        for (Object object : collection) {
            if (object instanceof Service) {
                Service service = (Service) object;
                Route route = createRouteForService(routeDomainPostfix, namespace, service, log);
                if (route != null) {
                    routes.add(route);
                }
            }
        }
        collection.addAll(routes);
    }

    protected void createIngress(Controller controller, KubernetesClient kubernetesClient, Collection<HasMetadata> collection) {
        String routeDomainPostfix = this.routeDomain;
        Log log = getLog();
        String namespace = clusterAccess.getNamespace();
        List<Ingress> ingressList = null;
        // lets get the routes first to see if we should bother
        try {
            IngressList ingresses = kubernetesClient.extensions().ingresses().inNamespace(namespace).list();
            if (ingresses != null) {
                ingressList = ingresses.getItems();
            }
        } catch (Exception e) {
            log.warn("Cannot load Ingress instances. Must be an older version of Kubernetes? Error: " + e, e);
            return;
        }
        List<Ingress> ingresses = new ArrayList<>();
        for (Object object : collection) {
            if (object instanceof Service) {
                Service service = (Service) object;
                if (!serviceHasIngressRule(ingressList, service)) {
                    Ingress ingress = createIngressForService(routeDomainPostfix, namespace, service, log);
                    if (ingress != null) {
                        ingresses.add(ingress);
                        log.info("Created ingress for " + namespace + ":" + KubernetesHelper.getName(service));
                    } else {
                        log.debug("No ingress required for " + namespace + ":" + KubernetesHelper.getName(service));
                    }
                } else {
                    log.info("Already has ingress for service " + namespace + ":" + KubernetesHelper.getName(service));
                }
            }
        }
        collection.addAll(ingresses);

    }

    /**
     * Returns true if there is an existing ingress rule for the given service
     */
    private boolean serviceHasIngressRule(List<Ingress> ingresses, Service service) {
        String serviceName = KubernetesHelper.getName(service);
        for (Ingress ingress : ingresses) {
            IngressSpec spec = ingress.getSpec();
            if (spec == null) {
                break;
            }
            List<IngressRule> rules = spec.getRules();
            if (rules == null) {
                break;
            }
            for (IngressRule rule : rules) {
                HTTPIngressRuleValue http = rule.getHttp();
                if (http == null) {
                    break;
                }
                List<HTTPIngressPath> paths = http.getPaths();
                if (paths == null) {
                    break;
                }
                for (HTTPIngressPath path : paths) {
                    IngressBackend backend = path.getBackend();
                    if (backend == null) {
                        break;
                    }
                    if (Objects.equals(serviceName, backend.getServiceName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    protected Controller createController() {
        Controller controller = new Controller(clusterAccess.createDefaultClient(log));
        controller.setThrowExceptionOnError(failOnError);
        controller.setRecreateMode(recreate);
        getLog().debug("Using recreate mode: " + recreate);
        return controller;
    }

    public String getRouteDomain() {
        return routeDomain;
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    public boolean isRecreate() {
        return recreate;
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

    protected void deleteEntities(KubernetesClient kubernetes, String namespace, Set<HasMetadata> entities) {
        List<HasMetadata> list = new ArrayList<>(entities);

        // For OpenShift cluster, also delete s2i buildconfig
        OpenShiftClient openshiftClient = new Controller(kubernetes).getOpenShiftClientOrNull();
        if (openshiftClient != null) {
            for (HasMetadata entity : list) {
                if ("ImageStream".equals(getKind(entity))) {
                    ImageName imageName = new ImageName(entity.getMetadata().getName());
                    String buildName = getS2IBuildName(imageName);
                    log.info("Deleting resource BuildConfig " + namespace + "/" + buildName);
                    openshiftClient.buildConfigs().inNamespace(namespace).withName(buildName).delete();
                }
            }
        }

        // lets delete in reverse order
        Collections.reverse(list);

        for (HasMetadata entity : list) {
            log.info("Deleting resource " + getKind(entity) + " " + namespace + "/" + getName(entity));
            kubernetes.resource(entity).inNamespace(namespace).cascading(true).delete();
        }
    }

    private String getS2IBuildName(ImageName imageName) {
        return imageName.getSimpleName() + s2iBuildNameSuffix;
    }

    protected void resizeApp(KubernetesClient kubernetes, String namespace, Set<HasMetadata> entities, int replicas) {
        for (HasMetadata entity : entities) {
            String name = getName(entity);
            Scaleable scalable = null;
            if (entity instanceof Deployment) {
                scalable = kubernetes.extensions().deployments().inNamespace(namespace).withName(name);
            } else if (entity instanceof ReplicaSet) {
                scalable = kubernetes.extensions().replicaSets().inNamespace(namespace).withName(name);
            } else if (entity instanceof ReplicationController) {
                scalable = kubernetes.replicationControllers().inNamespace(namespace).withName(name);
            } else if (entity instanceof DeploymentConfig) {
                OpenShiftClient openshiftClient = new Controller(kubernetes).getOpenShiftClientOrNull();
                if (openshiftClient == null) {
                    log.warn("Ignoring DeploymentConfig %s as not connected to an OpenShift cluster", name);
                    continue;
                }
                scalable = openshiftClient.deploymentConfigs().inNamespace(namespace).withName(name);
            }
            if (scalable != null) {
                log.info("Scaling " + getKind(entity) + " " + namespace + "/" + name + " to replicas: " + replicas);
                scalable.scale(replicas, true);
            }
        }
    }

    protected LabelSelector getPodLabelSelector(HasMetadata entity) {
        LabelSelector selector = null;
        if (entity instanceof Deployment) {
            Deployment resource = (Deployment) entity;
            DeploymentSpec spec = resource.getSpec();
            if (spec != null) {
                selector = spec.getSelector();
            }
        } else if (entity instanceof ReplicaSet) {
            ReplicaSet resource = (ReplicaSet) entity;
            ReplicaSetSpec spec = resource.getSpec();
            if (spec != null) {
                selector = spec.getSelector();
            }
        } else if (entity instanceof DeploymentConfig) {
            DeploymentConfig resource = (DeploymentConfig) entity;
            DeploymentConfigSpec spec = resource.getSpec();
            if (spec != null) {
                selector = toLabelSelector(spec.getSelector());
            }
        } else if (entity instanceof ReplicationController) {
            ReplicationController resource = (ReplicationController) entity;
            ReplicationControllerSpec spec = resource.getSpec();
            if (spec != null) {
                selector = toLabelSelector(spec.getSelector());
            }
        }
        return selector;
    }

    private LabelSelector toLabelSelector(Map<String, String> matchLabels) {
        if (matchLabels != null && !matchLabels.isEmpty()) {
            return new LabelSelectorBuilder().withMatchLabels(matchLabels).build();
        }
        return null;
    }

    protected FilterWatchListDeletable<Pod, PodList, Boolean, Watch, Watcher<Pod>> withSelector(ClientNonNamespaceOperation<Pod, PodList, DoneablePod, ClientPodResource<Pod, DoneablePod>> pods, LabelSelector selector) {
        FilterWatchListDeletable<Pod, PodList, Boolean, Watch, Watcher<Pod>> answer = pods;
        Map<String, String> matchLabels = selector.getMatchLabels();
        if (matchLabels != null && !matchLabels.isEmpty()) {
            answer = answer.withLabels(matchLabels);
        }
        List<LabelSelectorRequirement> matchExpressions = selector.getMatchExpressions();
        if (matchExpressions != null) {
            for (LabelSelectorRequirement expression : matchExpressions) {
                String key = expression.getKey();
                List<String> values = expression.getValues();
                if (Strings.isNullOrBlank(key)) {
                    log.warn("Ignoring empty key in selector expression %s", expression);
                    continue;
                }
                if (values == null && values.isEmpty()) {
                    log.warn("Ignoring empty values in selector expression %s", expression);
                    continue;
                }
                String[] valuesArray = values.toArray(new String[values.size()]);
                String operator = expression.getOperator();
                switch (operator) {
                    case "In":
                        answer = answer.withLabelIn(key, valuesArray);
                        break;
                    case "NotIn":
                        answer = answer.withLabelNotIn(key, valuesArray);
                        break;
                    default:
                        log.warn("Ignoring unknown operator %s in selector expression %s", operator, expression);
                }
            }
        }
        return answer;
    }

    protected File getKubeCtlExecutable(Controller controller) throws MojoExecutionException {
        OpenShiftClient openShiftClient = controller.getOpenShiftClientOrNull();
        String command = openShiftClient != null ? "oc" : "kubectl";

        String missingCommandMessage;
        File file = ProcessUtil.findExecutable(log, command);
        if (file == null && command.equals("oc")) {
            file = ProcessUtil.findExecutable(log, command);
            missingCommandMessage = "commands oc or kubectl";
        } else {
            missingCommandMessage = "command " + command;
        }
        if (file == null) {
            throw new MojoExecutionException("Could not find " + missingCommandMessage +
                    ". Please try running `mvn fabric8:install` to install the necessary binaries and ensure they get added to your $PATH");
        }
        return file;
    }

    protected String getPodCondition(Pod pod) {
        PodStatus podStatus = pod.getStatus();
        if (podStatus == null) {
            return "";
        }
        List<PodCondition> conditions = podStatus.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            return "";
        }


        for (PodCondition condition : conditions) {
            String type = condition.getType();
            if (Strings.isNotBlank(type)) {
                if ("ready".equalsIgnoreCase(type)) {
                    String statusText = condition.getStatus();
                    if (Strings.isNotBlank(statusText)) {
                        if (Boolean.parseBoolean(statusText)) {
                            return type;
                        }
                    }
                }
            }
        }
        return "";
    }

    protected String getPodStatusDescription(Pod pod) {
        return KubernetesHelper.getPodStatusText(pod) + " " + getPodCondition(pod);
    }

    protected String getPodStatusMessagePostfix(Watcher.Action action) {
        String message = "";
        switch (action) {
            case DELETED:
                message = ": Pod Deleted";
                break;
            case ERROR:
                message = ": Error";
                break;
        }
        return message;
    }
}
