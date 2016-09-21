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

import io.fabric8.kubernetes.api.Controller;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerSpec;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpec;
import io.fabric8.kubernetes.api.model.extensions.LabelSelector;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.maven.core.util.ProcessUtil;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigSpec;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.utils.Objects;
import io.fabric8.utils.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static io.fabric8.kubernetes.api.KubernetesHelper.getKind;
import static io.fabric8.kubernetes.api.KubernetesHelper.getName;
import static io.fabric8.kubernetes.api.KubernetesHelper.isPodReady;
import static io.fabric8.kubernetes.api.KubernetesHelper.isPodRunning;
import static io.fabric8.maven.core.util.ProcessUtil.processCommandAsync;
import static io.fabric8.maven.plugin.AbstractInstallMojo.GOFABRIC8;
import static org.bouncycastle.asn1.x500.style.RFC4519Style.name;
import static org.json.XMLTokener.entity;

/**
 * Ensures that the current app has debug enabled, then opens the debug port so that you can debug the latest pod
 * from your IDE
 */
@Mojo(name = "debug", requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.INSTALL)
public class DebugMojo extends AbstractDeployMojo {

    public static final String ENV_VAR_JAVA_DEBUG = "JAVA_ENABLE_DEBUG";
    public static final String ENV_VAR_JAVA_DEBUG_PORT = "JAVA_DEBUG_PORT";
    public static final String ENV_VAR_JAVA_DEBUG_PORT_DEFAULT = "5005";

    @Parameter(property = "fabric8.debug.port", defaultValue = "5005")
    private String localDebugPort;

    private String remoteDebugPort = ENV_VAR_JAVA_DEBUG_PORT_DEFAULT;
    private Watch podWatcher;
    private CountDownLatch terminateLatch = new CountDownLatch(1);
    private Pod foundPod;
    private Logger podWaitLog;

    @Override
    protected void applyEntities(Controller controller, KubernetesClient kubernetes, String namespace, String fileName, Set<HasMetadata> entities) throws Exception {
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
                            log.warn("Ignoring DeploymentConfig " + name + " as not connected to an OpenShift cluster");
                            continue;
                        }
                        openshiftClient.deploymentConfigs().inNamespace(namespace).withName(name).replace(resource);
                    }
                    selector = getPodLabelSelector(entity);
                }
            }

            if (selector != null) {
                String podName = waitForRunningPodWithEnvVar(kubernetes, namespace, selector, ENV_VAR_JAVA_DEBUG, "true");
                portForward(controller, podName);
                break;
            }
        }
    }

    private String waitForRunningPodWithEnvVar(final KubernetesClient kubernetes, final String namespace, LabelSelector selector, final String envVarName, final String envVarValue) throws MojoExecutionException {
        //  wait for the newest pod to be ready with the given env var
        FilterWatchListDeletable<Pod, PodList, Boolean, Watch, Watcher<Pod>> pods = withSelector(kubernetes.pods().inNamespace(namespace), selector);
        log.info("Waiting for debug pod with selector " + selector + " and $" + envVarName + " = " + envVarValue);
        podWaitLog = createExternalProcessLogger("pod wait> ");
        PodList list = pods.list();
        if (list != null) {
            List<Pod> items = list.getItems();
            Pod latestPod = getNewestPod(list.getItems());
            if (latestPod != null && podHasEnvVarValue(latestPod, envVarName, envVarValue)) {
                return getName(latestPod);
            }
        }
        podWatcher = pods.watch(new Watcher<Pod>() {
            @Override
            public void eventReceived(Watcher.Action action, Pod pod) {
                podWaitLog.info("" + action + " pod " + getName(pod) + " status: " + getPodStatusDescription(pod));

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
        File file = getKubeCtlExecutable(controller);
        String command = file.getName();
        log.info("Port forwarding to port " + remoteDebugPort + " on pod " + podName + " using command: " + command);

        String arguments = " port-forward " + podName + " " + localDebugPort + ":" + remoteDebugPort;
        String commands = command + arguments;
        log.info("Executing command: " + commands);
        final String message = "port forward";
        final Process process;
        try {
            process = Runtime.getRuntime().exec(file.getAbsolutePath() + arguments);
            Runtime.getRuntime().addShutdownHook(new Thread("mvn fabric8:run-interactive shutdown hook") {
                @Override
                public void run() {
                    if (process != null) {
                        log.info("Terminating port forward process:");
                        try {
                            process.destroy();
                        } catch (Exception e) {
                            log.error("Failed to terminate process " + message);
                        }
                        try {
                            if (process != null && process.isAlive()) {
                                process.destroyForcibly();
                            }
                        } catch (Exception e) {
                            log.error("Failed to forcibly terminate process " + message);
                        }
                    }
                }
            });

            log.info("");
            log.info("Now you can start a Remote debug execution in your IDE by using localhost and the debug port " + localDebugPort);
            log.info("");

            processCommandAsync(process, createExternalProcessLogger(command + "> "), commands, message);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to execute process " + commands + " for " +
                    message + ": " + e, e);
        }
    }

    private boolean enableDebugging(HasMetadata entity, PodTemplateSpec template) {
        if (template != null) {
            PodSpec podSpec = template.getSpec();
            if (podSpec != null) {
                List<Container> containers = podSpec.getContainers();
                if (containers.size() > 0) {
                    Container container = containers.get(0);
                    List<EnvVar> env = container.getEnv();
                    if (env == null) {
                        env = new ArrayList<>();
                    }
                    remoteDebugPort = getEnvVar(env, ENV_VAR_JAVA_DEBUG_PORT, ENV_VAR_JAVA_DEBUG_PORT_DEFAULT);
                    boolean enabled = false;
                    if (setEnvVar(env, ENV_VAR_JAVA_DEBUG, "true")) {
                        container.setEnv(env);
                        enabled = true;
                    }
                    List<ContainerPort> ports = container.getPorts();
                    if (ports == null) {
                        ports = new ArrayList<>();
                    }
                    if (addPort(ports, remoteDebugPort, "debug")) {
                        container.setPorts(ports);
                        enabled = true;
                    }
                    if (enabled) {
                        log.info("Enabling debug on " + getKind(entity) + " " + getName(entity));
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean addPort(List<ContainerPort> ports, String portNumberText, String portName) {
        if (Strings.isNullOrBlank(portNumberText)) {
            return false;
        }
        int portValue;
        try {
            portValue = Integer.parseInt(portNumberText);
        } catch (NumberFormatException e) {
            log.warn("Could not parse remote debugging port " + portNumberText + " as an integer: " + e, e);
            return false;
        }
        for (ContainerPort port : ports) {
            String name = port.getName();
            Integer containerPort = port.getContainerPort();
            if (containerPort != null && containerPort.intValue() == portValue) {
                return false;
            }
        }
        ports.add(new ContainerPortBuilder().withName(portName).withContainerPort(portValue).build());
        return true;
    }

    private boolean setEnvVar(List<EnvVar> envVarList, String name, String value) {
        for (EnvVar envVar : envVarList) {
            String envVarName = envVar.getName();
            if (Objects.equal(name, envVarName)) {
                String oldValue = envVar.getValue();
                if (Objects.equal(value, oldValue)) {
                    return false;
                } else {
                    envVar.setValue(value);
                    return true;
                }
            }
        }
        EnvVar env = new EnvVarBuilder().withName(name).withValue(value).build();
        envVarList.add(env);
        return true;
    }

    private String getEnvVar(List<EnvVar> envVarList, String name, String defaultValue) {
        String answer = defaultValue;
        if (envVarList != null) {
            for (EnvVar envVar : envVarList) {
                String envVarName = envVar.getName();
                if (Objects.equal(name, envVarName)) {
                    String value = envVar.getValue();
                    if (Strings.isNotBlank(value)) {
                        return value;
                    }
                }
            }
        }
        return answer;
    }


}


