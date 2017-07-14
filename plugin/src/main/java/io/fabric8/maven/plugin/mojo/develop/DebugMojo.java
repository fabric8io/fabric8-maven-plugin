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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import io.fabric8.kubernetes.api.Controller;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerSpec;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpec;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.maven.core.service.Fabric8ServiceException;
import io.fabric8.maven.core.util.DebugConstants;
import io.fabric8.maven.core.util.KubernetesResourceUtil;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.plugin.mojo.build.ApplyMojo;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigSpec;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.utils.Objects;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import static io.fabric8.kubernetes.api.KubernetesHelper.getKind;
import static io.fabric8.kubernetes.api.KubernetesHelper.getName;
import static io.fabric8.kubernetes.api.KubernetesHelper.isPodReady;
import static io.fabric8.kubernetes.api.KubernetesHelper.isPodRunning;
import static io.fabric8.maven.core.util.KubernetesClientUtil.getPodStatusDescription;
import static io.fabric8.maven.core.util.KubernetesClientUtil.getPodStatusMessagePostfix;
import static io.fabric8.maven.core.util.KubernetesClientUtil.withSelector;
import static io.fabric8.maven.core.util.KubernetesResourceUtil.getPodLabelSelector;

/**
 * Ensures that the current app has debug enabled, then opens the debug port so that you can debug the latest pod
 * from your IDE
 */
@Mojo(name = "debug", requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.PACKAGE)
public class DebugMojo extends ApplyMojo {

    @Parameter(property = "fabric8.debug.port", defaultValue = "5005")
    private String localDebugPort;

    private String remoteDebugPort = DebugConstants.ENV_VAR_JAVA_DEBUG_PORT_DEFAULT;
    private Watch podWatcher;
    private CountDownLatch terminateLatch = new CountDownLatch(1);
    private Pod foundPod;
    private Logger podWaitLog;

    @Override
    protected void applyEntities(Controller controller, KubernetesClient kubernetes, String namespace, String fileName, Set<HasMetadata> entities) throws Exception {
        LabelSelector firstSelector = null;
        for (HasMetadata entity : entities) {
            String name = getName(entity);
            LabelSelector selector = null;
            if (entity instanceof Deployment) {
                Deployment resource = (Deployment) entity;
                DeploymentSpec spec = resource.getSpec();
                if (spec != null) {
                    if (enableDebugging(entity, spec.getTemplate())) {
                        kubernetes.extensions().deployments().inNamespace(namespace).withName(name).replace(resource);
                    }
                    selector = getPodLabelSelector(entity);
                }
            } else if (entity instanceof ReplicaSet) {
                ReplicaSet resource = (ReplicaSet) entity;
                ReplicaSetSpec spec = resource.getSpec();
                if (spec != null) {
                    if (enableDebugging(entity, spec.getTemplate())) {
                        kubernetes.extensions().replicaSets().inNamespace(namespace).withName(name).replace(resource);
                    }
                    selector = getPodLabelSelector(entity);
                }
            } else if (entity instanceof ReplicationController) {
                ReplicationController resource = (ReplicationController) entity;
                ReplicationControllerSpec spec = resource.getSpec();
                if (spec != null) {
                    if (enableDebugging(entity, spec.getTemplate())) {
                        kubernetes.replicationControllers().inNamespace(namespace).withName(name).replace(resource);
                    }
                    selector = getPodLabelSelector(entity);
                }
            } else if (entity instanceof DeploymentConfig) {
                DeploymentConfig resource = (DeploymentConfig) entity;
                DeploymentConfigSpec spec = resource.getSpec();
                if (spec != null) {
                    if (enableDebugging(entity, spec.getTemplate())) {
                        OpenShiftClient openshiftClient = new Controller(kubernetes).getOpenShiftClientOrNull();
                        if (openshiftClient == null) {
                            log.warn("Ignoring DeploymentConfig %s as not connected to an OpenShift cluster", name);
                            continue;
                        }
                        openshiftClient.deploymentConfigs().inNamespace(namespace).withName(name).replace(resource);
                    }
                    selector = getPodLabelSelector(entity);
                }
            }
            if (selector != null) {
                firstSelector = selector;
            } else {
                controller.apply(entity, fileName);
            }
        }
        if (firstSelector != null) {
            String podName = waitForRunningPodWithEnvVar(kubernetes, namespace, firstSelector, DebugConstants.ENV_VAR_JAVA_DEBUG, "true");
            portForward(controller, podName);
        }
    }

    private String waitForRunningPodWithEnvVar(final KubernetesClient kubernetes, final String namespace, LabelSelector selector, final String envVarName, final String envVarValue) throws MojoExecutionException {
        //  wait for the newest pod to be ready with the given env var
        FilterWatchListDeletable<Pod, PodList, Boolean, Watch, Watcher<Pod>> pods = withSelector(kubernetes.pods().inNamespace(namespace), selector, log);
        log.info("Waiting for debug pod with selector " + selector + " and $" + envVarName + " = " + envVarValue);
        podWaitLog = createExternalProcessLogger("[[Y]][W][[Y]] ");
        PodList list = pods.list();
        if (list != null) {
            List<Pod> items = list.getItems();
            Pod latestPod = KubernetesResourceUtil.getNewestPod(list.getItems());
            if (latestPod != null && podHasEnvVarValue(latestPod, envVarName, envVarValue)) {
                return getName(latestPod);
            }
        }
        podWatcher = pods.watch(new Watcher<Pod>() {
            @Override
            public void eventReceived(Watcher.Action action, Pod pod) {
                podWaitLog.info(getName(pod) + " status: " + getPodStatusDescription(pod) + getPodStatusMessagePostfix(action));

                if (isAddOrModified(action) && isPodRunning(pod) && isPodReady(pod) &&
                        podHasEnvVarValue(pod, envVarName, envVarValue)) {
                    foundPod = pod;
                    terminateLatch.countDown();
                }
            }

            @Override
            public void onClose(KubernetesClientException e) {
                // ignore

            }
        });

        // now lets wait forever?
        while (terminateLatch.getCount() > 0) {
            try {
                terminateLatch.await();
            } catch (InterruptedException e) {
                // ignore
            }
            if (foundPod != null) {
                return getName(foundPod);
            }
        }
        throw new MojoExecutionException("Could not find a running pod with $" + envVarName + " = " + envVarValue);
    }

    private boolean isAddOrModified(Watcher.Action action) {
        return action.equals(Watcher.Action.ADDED) || action.equals(Watcher.Action.MODIFIED);
    }

    private boolean podHasEnvVarValue(Pod pod, String envVarName, String envVarValue) {
        PodSpec spec = pod.getSpec();
        if (spec != null) {
            List<Container> containers = spec.getContainers();
            if (containers != null && !containers.isEmpty()) {
                Container container = containers.get(0);
                List<EnvVar> env = container.getEnv();
                if (env != null) {
                    for (EnvVar envVar : env) {
                        if (Objects.equal(envVar.getName(), envVarName) && Objects.equal(envVar.getValue(), envVarValue)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }


    private void portForward(Controller controller, String podName) throws MojoExecutionException {
        try {
            getFabric8ServiceHub(controller).getPortForwardService()
                    .forwardPort(createExternalProcessLogger("[[B]]port-forward[[B]] "), podName, portToInt(remoteDebugPort, "remoteDebugPort"), portToInt(localDebugPort, "localDebugPort"));

            log.info("");
            log.info("Now you can start a Remote debug execution in your IDE by using localhost and the debug port " + localDebugPort);
            log.info("");

        } catch (Fabric8ServiceException e) {
            throw new MojoExecutionException("Failed to start port forwarding" + e, e);
        }
    }

    private boolean enableDebugging(HasMetadata entity, PodTemplateSpec template) {
        if (template != null) {
            PodSpec podSpec = template.getSpec();
            if (podSpec != null) {
                List<Container> containers = podSpec.getContainers();
                boolean enabled = false;
                 for (int i = 0; i < containers.size(); i++) {
                    Container container = containers.get(i);
                    List<EnvVar> env = container.getEnv();
                    if (env == null) {
                        env = new ArrayList<>();
                    }
                    remoteDebugPort = KubernetesResourceUtil.getEnvVar(env, DebugConstants.ENV_VAR_JAVA_DEBUG_PORT, DebugConstants.ENV_VAR_JAVA_DEBUG_PORT_DEFAULT);
                    if (KubernetesResourceUtil.setEnvVar(env, DebugConstants.ENV_VAR_JAVA_DEBUG, "true")) {
                        container.setEnv(env);
                        enabled = true;
                    }
                    List<ContainerPort> ports = container.getPorts();
                    if (ports == null) {
                        ports = new ArrayList<>();
                    }
                    if (KubernetesResourceUtil.addPort(ports, remoteDebugPort, "debug", log)) {
                        container.setPorts(ports);
                        enabled = true;
                    }
                }
                if (enabled) {
                    log.info("Enabling debug on " + getKind(entity) + " " + getName(entity));
                    return true;
                }
            }
        }
        return false;
    }

    private int portToInt(String port, String name) throws MojoExecutionException {
        try {
            int portInt = Integer.parseInt(port);
            return portInt;
        } catch (Exception e) {
            throw new MojoExecutionException("Invalid port value: " + name +"=" + port);
        }
    }

}


