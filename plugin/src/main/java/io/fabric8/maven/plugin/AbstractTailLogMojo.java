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

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.PodStatusType;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.extensions.LabelSelector;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.ClientPodResource;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.utils.Strings;
import org.apache.maven.plugins.annotations.Parameter;
import org.fusesource.jansi.Ansi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import static io.fabric8.kubernetes.api.KubernetesHelper.getName;
import static io.fabric8.kubernetes.api.KubernetesHelper.getPodStatus;
import static io.fabric8.kubernetes.api.KubernetesHelper.isPodRunning;

/**
 */
public class AbstractTailLogMojo extends AbstractDeployMojo {
    public static final String OPERATION_UNDEPLOY = "undeploy";
    public static final String OPERATION_STOP = "stop";
    public static final String FABRIC8_LOG_CONTAINER = "fabric8.log.container";

    @Parameter(property = FABRIC8_LOG_CONTAINER, defaultValue = "5005")
    private String logContainerName;

    private Watch podWatcher;
    private LogWatch logWatcher;
    private Map<String, Pod> addedPods = new ConcurrentHashMap<>();
    private CountDownLatch terminateLatch = new CountDownLatch(1);
    private String watchingPodName;
    private String newestPodName;
    private CountDownLatch logWatchTerminateLatch;
    private Logger newPodLog;
    private Logger oldPodLog;

    protected void tailAppPodsLogs(final KubernetesClient kubernetes, final String namespace, final Set<HasMetadata> entities, boolean watchAddedPodsOnly, String onExitOperation, boolean followLog, Date ignorePodsOlderThan) {
        LabelSelector selector = null;
        for (HasMetadata entity : entities) {
            selector = getPodLabelSelector(entity);
            if (selector != null) {
                break;
            }
        }
        newPodLog = createLogger("New Pod> ", Ansi.Color.CYAN);
        oldPodLog = createLogger("Old Pod> ", Ansi.Color.DEFAULT);
        if (selector != null) {
            String ctrlCMessage = "stop tailing the log";
            if (Strings.isNotBlank(onExitOperation)) {
                final String onExitOperationLower = onExitOperation.toLowerCase().trim();
                if (onExitOperationLower.equals(OPERATION_UNDEPLOY)) {
                    ctrlCMessage = "undeploy the app";
                } else if (onExitOperationLower.equals(OPERATION_STOP)) {
                    ctrlCMessage = "scale down the app and stop tailing the log";
                } else {
                    log.warn("Unknown on-exit command: `" + onExitOperationLower + "`");
                }

                Runtime.getRuntime().addShutdownHook(new Thread("mvn fabric8:run-interactive shutdown hook") {
                    @Override
                    public void run() {
                        if (onExitOperationLower.equals(OPERATION_UNDEPLOY)) {
                            log.info("Undeploying the app:");
                            deleteEntities(kubernetes, namespace, entities);
                        } else if (onExitOperationLower.equals(OPERATION_STOP)) {
                            log.info("Stopping the app:");
                            resizeApp(kubernetes, namespace, entities, 0);
                        }
                        if (podWatcher != null) {
                            podWatcher.close();
                        }
                        closeLogWatcher();
                    }
                });
            }
            waitAndLogPods(kubernetes, namespace, selector, watchAddedPodsOnly, ctrlCMessage, followLog, ignorePodsOlderThan);
        } else {
            log.warn("No selector in deployment so cannot watch pods!");
        }
    }

    private void waitAndLogPods(final KubernetesClient kubernetes, final String namespace, LabelSelector selector, final boolean watchAddedPodsOnly, final String ctrlCMessage, final boolean followLog, Date ignorePodsOlderThan) {
        FilterWatchListDeletable<Pod, PodList, Boolean, Watch, Watcher<Pod>> pods = withSelector(kubernetes.pods().inNamespace(namespace), selector);
        log.info("Watching pods with selector " + selector + " waiting for a running pod...");
        Pod latestPod = null;
        boolean runningPod = false;
        PodList list = pods.list();
        if (list != null) {
            List<Pod> items = list.getItems();
            if (items != null) {
                for (Pod pod : items) {
                    PodStatusType status = getPodStatus(pod);
                    switch (status) {
                        case WAIT:
                        case OK:
                            if (latestPod == null || isNewerPod(pod, latestPod)) {
                                if (ignorePodsOlderThan != null) {
                                    Date podCreateTime = getCreationTimestamp(pod);
                                    if (podCreateTime != null && podCreateTime.compareTo(ignorePodsOlderThan) > 0) {
                                        latestPod = pod;
                                    }
                                } else {
                                    latestPod = pod;
                                }
                            }
                            runningPod = true;
                            break;

                        case ERROR:
                        default:
                            continue;
                    }
                }
            }
        }
        // we may have missed the ADDED event so lets simulate one
        if (latestPod != null) {
            onPod(Watcher.Action.ADDED, latestPod, kubernetes, namespace, ctrlCMessage, followLog);
        }
        if (!watchAddedPodsOnly) {
            // lets watch the current pods then watch for changes
            if (!runningPod) {
                log.warn("No pod is running yet. Are you sure you deployed your app via `fabric8:deploy`?");
                log.warn("Or did you stop it via `fabric8:stop`? If so try running the `fabric8:start` goal");
            }
        }
        podWatcher = pods.watch(new Watcher<Pod>() {
            @Override
            public void eventReceived(Action action, Pod pod) {
                onPod(action, pod, kubernetes, namespace, ctrlCMessage, followLog);
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
        }
    }

    private void onPod(Watcher.Action action, Pod pod, KubernetesClient kubernetes, String namespace, String ctrlCMessage, boolean followLog) {
        String name = getName(pod);
        if (action.equals(Watcher.Action.DELETED)) {
            addedPods.remove(name);
            if (Objects.equals(watchingPodName, name)) {
                watchingPodName = null;
                addedPods.remove(name);
            }
        } else {
            if (action.equals(Watcher.Action.ADDED) || action.equals(Watcher.Action.MODIFIED)) {
                addedPods.put(name, pod);
            }
        }

        Pod watchPod = getNewestPod(addedPods.values());
        newestPodName = getName(watchPod);

        Logger statusLog = Objects.equals(name, newestPodName) ? newPodLog : oldPodLog;
        if (!action.equals(Watcher.Action.MODIFIED) || watchingPodName == null || !watchingPodName.equals(name)) {
            statusLog.info(name + " status: " + getPodStatusDescription(pod) + getPodStatusMessagePostfix(action));
        }

        if (watchPod != null && isPodRunning(watchPod)) {
            watchLogOfPodName(kubernetes, namespace, ctrlCMessage, followLog, watchPod, getName(watchPod));
        }
    }

    private void watchLogOfPodName(KubernetesClient kubernetes, String namespace, String ctrlCMessage, boolean followLog, Pod pod, String name) {
        if (watchingPodName == null || !watchingPodName.equals(name)) {
            if (logWatcher != null) {
                log.info("Closing log watcher for " + watchingPodName + " as now watching " + name);
                closeLogWatcher();

            }
            ClientPodResource<Pod, DoneablePod> podResource = kubernetes.pods().inNamespace(namespace).withName(name);
            List<Container> containers = KubernetesHelper.getContainers(pod);
            if (followLog) {
                watchingPodName = name;
                logWatchTerminateLatch = new CountDownLatch(1);
                if (containers.size() < 2) {
                    logWatcher = podResource.watchLog();
                } else {
                    logWatcher = podResource.inContainer(getLogContainerName(containers)).watchLog();
                }
                watchLog(logWatcher, name, "Failed to read log of pod " + name + ".", ctrlCMessage);
            } else {
                String logText;
                if (containers.size() < 2) {
                    logText = podResource.getLog();
                } else {
                    logText = podResource.inContainer(getLogContainerName(containers)).getLog();
                }
                if (logText != null) {
                    String[] lines = logText.split("\n");
                    Logger log = createPodLogger();
                    log.info("Log of pod: " + name);
                    log.info("");
                    for (String line : lines) {
                        log.info(line);
                    }
                }
                terminateLatch.countDown();
            }
        }
    }

    private String getLogContainerName(List<Container> containers) {
        if (Strings.isNotBlank(logContainerName)) {
            for (Container container : containers) {
                if (Objects.equals(logContainerName, container.getName())) {
                    return logContainerName;
                }
            }
            log.error("log container name " + logContainerName + " does not exist in pod!! Did you set the correct value for property " + FABRIC8_LOG_CONTAINER);
        }
        return containers.get(0).getName();
    }

    private void closeLogWatcher() {
        if (logWatcher != null) {
            logWatcher.close();
            logWatcher = null;
        }
        if (logWatchTerminateLatch != null) {
            logWatchTerminateLatch.countDown();
        }
    }

    private void watchLog(final LogWatch logWatcher, String podName, final String failureMessage, String ctrlCMessage) {
        final InputStream in = logWatcher.getOutput();
        final Logger log = createPodLogger();
        newPodLog.info("Tailing log of pod: " + podName);
        newPodLog.info("Press Ctrl-C to " + ctrlCMessage);
        newPodLog.info("");

        Thread thread = new Thread() {
            @Override
            public void run() {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                    while (true) {
                        String line = reader.readLine();
                        if (line == null) {
                            log.info("Log closed");
                            return;
                        }
                        if (logWatchTerminateLatch.getCount() <= 0L) {
                            return;
                        }
                        log.info("%s", line);
                    }
                } catch (IOException e) {
                    log.error("%s : %s",failureMessage, e);
                }
            }
        };
        thread.start();
    }

    private Logger createPodLogger() {
        return createExternalProcessLogger("Pod> ");
    }

    private boolean isNewerPod(HasMetadata newer, HasMetadata older) {
        Date t1 = getCreationTimestamp(newer);
        Date t2 = getCreationTimestamp(older);
        if (t1 != null) {
            return t2 == null || t1.compareTo(t2) > 0;
        }
        return false;
    }

}
