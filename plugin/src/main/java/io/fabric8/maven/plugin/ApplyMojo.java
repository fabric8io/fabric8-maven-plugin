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


import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.Controller;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.internal.HasMetadataComparator;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteList;
import io.fabric8.openshift.api.model.RouteSpec;
import io.fabric8.openshift.api.model.Template;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.utils.Files;
import io.fabric8.utils.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Applies the Kubernetes UYAML to a namespace in a kubernetes environment
 */
@Mojo(name = "apply", requiresDependencyResolution = ResolutionScope.RUNTIME, defaultPhase = LifecyclePhase.INSTALL)
public class ApplyMojo extends AbstractNamespacedMojo {

    /**
     * The generated kubernetes YAML file
     */
    @Parameter(property = "fabric8.manifest", defaultValue = "${basedir}/target/classes/fabric8.yaml")
    private File kubernetesManifest;


    @Parameter(defaultValue = "${project}", readonly = true, required = false)
    private MavenProject project;


    /**
     * Should we create new kubernetes resources?
     */
    @Parameter(property = "fabric8.apply.create", defaultValue = "true")
    private boolean createNewResources;

    /**
     * Should we use rolling upgrades to apply changes?
     */
    @Parameter(property = "fabric8.rolling", defaultValue = "false")
    private boolean rollingUpgrades;

    /**
     * Should we fail if there is no kubernetes json
     */
    @Parameter(property = "fabric8.apply.failOnNoKubernetesJson", defaultValue = "false")
    private boolean failOnNoKubernetesJson;

    /**
     * In services only mode we only process services so that those can be recursively created/updated first
     * before creating/updating any pods and replication controllers
     */
    @Parameter(property = "fabric8.apply.servicesOnly", defaultValue = "false")
    private boolean servicesOnly;

    /**
     * Do we want to ignore services. This is particularly useful when in recreate mode
     * to let you easily recreate all the ReplicationControllers and Pods but leave any service
     * definitions alone to avoid changing the portalIP addresses and breaking existing pods using
     * the service.
     */
    @Parameter(property = "fabric8.apply.ignoreServices", defaultValue = "false")
    private boolean ignoreServices;

    /**
     * Process templates locally in Java so that we can apply OpenShift templates on any Kubernetes environment
     */
    @Parameter(property = "fabric8.apply.processTemplatesLocally", defaultValue = "false")
    private boolean processTemplatesLocally;

    /**
     * Should we delete all the pods if we update a Replication Controller
     */
    @Parameter(property = "fabric8.apply.deletePods", defaultValue = "true")
    private boolean deletePodsOnReplicationControllerUpdate;

    /**
     * Do we want to ignore OAuthClients which are already running?. OAuthClients are shared across namespaces
     * so we should not try to update or create/delete global oauth clients
     */
    @Parameter(property = "fabric8.apply.ignoreRunningOAuthClients", defaultValue = "true")
    private boolean ignoreRunningOAuthClients;

    /**
     * Should we create routes for any services which don't already have them.
     */
    @Parameter(property = "fabric8.apply.createRoutes", defaultValue = "true")
    private boolean createRoutes;

    /**
     * The folder we should store any temporary json files or results
     */
    @Parameter(property = "fabric8.apply.jsonLogDir", defaultValue = "${basedir}/target/fabric8/applyJson")
    private File jsonLogDir;

    public static Route createRouteForService(String routeDomainPostfix, String namespace, Service service, Log log) {
        Route route = null;
        String id = KubernetesHelper.getName(service);
        if (Strings.isNotBlank(id) && shouldCreateRouteForService(log, service, id)) {
            route = new Route();
            String routeId = id;
            KubernetesHelper.setName(route, namespace, routeId);
            RouteSpec routeSpec = new RouteSpec();
            ObjectReference objectRef = new ObjectReference();
            objectRef.setName(id);
            objectRef.setNamespace(namespace);
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

    /**
     * Should we try to create a route for the given service?
     * <p/>
     * By default lets ignore the kubernetes services and any service which does not expose ports 80 and 443
     *
     * @return true if we should create an OpenShift Route for this service.
     */
    protected static boolean shouldCreateRouteForService(Log log, Service service, String id) {
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

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File manifest = kubernetesManifest;
        if (!Files.isFile(manifest)) {
            if (failOnNoKubernetesJson) {
                throw new MojoFailureException("No such generated kubernetes YAML file: " + manifest);
            } else {
                getLog().warn("No such generated kubernetes YAML file: " + manifest + " for this project so ignoring");
                return;
            }
        }
        KubernetesClient kubernetes = getKubernetes();

        if (kubernetes.getMasterUrl() == null || Strings.isNullOrBlank(kubernetes.getMasterUrl().toString())) {
            throw new MojoFailureException("Cannot find Kubernetes master URL");
        }
        getLog().info("Using kubernetes at: " + kubernetes.getMasterUrl() + " in namespace " + getNamespace() + " Kubernetes manifest: " + manifest);

        try {
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


            String fileName = manifest.getName();
            Object dto = KubernetesHelper.loadYaml(manifest, KubernetesResource.class);
            if (dto == null) {
                throw new MojoFailureException("Cannot load kubernetes YAML: " + manifest);
            }

            // lets check we have created the namespace
            String namespace = getNamespace();
            controller.applyNamespace(namespace);
            controller.setNamespace(namespace);

            if (dto instanceof Template) {
                Template template = (Template) dto;
                dto = applyTemplates(template, kubernetes, controller, fileName);
            }

            Set<KubernetesResource> resources = new LinkedHashSet<>();

            Set<HasMetadata> entities = new TreeSet<>(new HasMetadataComparator());
            for (KubernetesResource resource : resources) {
                entities.addAll(KubernetesHelper.toItemList(resource));
            }

            entities.addAll(KubernetesHelper.toItemList(dto));

            if (createRoutes) {
                createRoutes(controller, entities);
            }

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
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    public boolean isRollingUpgrades() {
        return rollingUpgrades;
    }

    public void setRollingUpgrades(boolean rollingUpgrades) {
        this.rollingUpgrades = rollingUpgrades;
    }

    public boolean isRollingUpgradePreserveScale() {
        return false;
    }

    @Override
    public MavenProject getProject() {
        return project;
    }

    /**
     * Lets disable OpenShift-only features if we are not running on OpenShift
     */
    protected void disableOpenShiftFeatures(Controller controller) {
        // TODO we could check if the Templates service is running and if so we could still support templates?
        this.processTemplatesLocally = true;
        this.createRoutes = false;
        controller.setSupportOAuthClients(false);
        controller.setProcessTemplatesLocally(true);
    }

    protected Object applyTemplates(Template template, KubernetesClient kubernetes, Controller controller, String fileName) throws Exception {
        KubernetesHelper.setNamespace(template, getNamespace());
        overrideTemplateParameters(template);
        return controller.applyTemplate(template, fileName);
    }

    /**
     * Before applying the given template lets allow template parameters to be overridden via the maven
     * properties - or optionally - via the command line if in interactive mode.
     */
    protected void overrideTemplateParameters(Template template) {
        List<io.fabric8.openshift.api.model.Parameter> parameters = template.getParameters();
        MavenProject project = getProject();
        if (parameters != null && project != null) {
            Properties properties = getProjectAndFabric8Properties(project);
            boolean missingProperty = false;
            for (io.fabric8.openshift.api.model.Parameter parameter : parameters) {
                String parameterName = parameter.getName();
                String name = "fabric8.apply." + parameterName;
                String propertyValue = properties.getProperty(name);
                if (propertyValue != null) {
                    getLog().info("Overriding template parameter " + name + " with value: " + propertyValue);
                    parameter.setValue(propertyValue);
                } else {
                    missingProperty = true;
                    getLog().info("No property defined for template parameter: " + name);
                }
            }
            if (missingProperty) {
                getLog().debug("Current properties " + new TreeSet<>(properties.keySet()));
            }
        }
    }

    protected Properties getProjectAndFabric8Properties(MavenProject project) {
        Properties properties = project.getProperties();
        properties.putAll(project.getProperties());
        // let system properties override so we can read from the command line
        properties.putAll(System.getProperties());
        return properties;
    }

    protected void createRoutes(Controller controller, Collection<HasMetadata> collection) {
        String routeDomainPostfix = this.routeDomain;
        Log log = getLog();
        String namespace = getNamespace();
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
}
