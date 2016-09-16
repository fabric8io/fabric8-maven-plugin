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
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerSpec;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpec;
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
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigSpec;
import io.fabric8.utils.Strings;
import org.fusesource.jansi.Ansi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import static io.fabric8.kubernetes.api.KubernetesHelper.parseDate;

/**
 */
public class AbstractTailLogMojo extends AbstractDeployMojo {
    public static final Ansi.Color COLOR_POD_LOG = Ansi.Color.BLUE;
    private Watch podWatcher;
    private LogWatch logWatcher;
    private Map<String, Pod> addedPods = new ConcurrentHashMap<>();
    private CountDownLatch terminateLatch = new CountDownLatch(1);
    private String watchingPodName;
    private CountDownLatch logWatchTerminateLatch;

    protected void tailAppPodsLogs(final KubernetesClient kubernetes, final String namespace, final Set<HasMetadata> entities, boolean watchAddedPodsOnly, boolean deleteAppOnExit, boolean followLog, Date ignorePodsOlderThan) {
        LabelSelector selector = null;
        for (HasMetadata entity : entities) {
            selector = getPodLabelSelector(entity);
            if (selector != null) {
                break;
            }
        }
        if (selector != null) {
            if (deleteAppOnExit) {
                Runtime.getRuntime().addShutdownHook(new Thread("mvn fabric8:run-interactive shutdown hook") {
                    @Override
                    public void run() {
                        log.info("Undeploying the app:");
                        deleteEntities(kubernetes, namespace, entities);

                        if (podWatcher != null) {
                            podWatcher.close();
                        }
                        closeLogWatcher();
                    }
                });
            }
            waitAndLogPods(kubernetes, namespace, selector, watchAddedPodsOnly, deleteAppOnExit, followLog, ignorePodsOlderThan);
        } else {
            log.warn("No selector in deployment so cannot watch pods!");
        }
    }

    private void waitAndLogPods(final KubernetesClient kubernetes, final String namespace, LabelSelector selector, final boolean watchAddedPodsOnly, final boolean deleteAppOnExit, final boolean followLog, Date ignorePodsOlderThan) {
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
            onPod(Watcher.Action.ADDED, latestPod, kubernetes, namespace, deleteAppOnExit, followLog);
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
                onPod(action, pod, kubernetes, namespace, deleteAppOnExit, followLog);
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

    private void onPod(Watcher.Action action, Pod pod, KubernetesClient kubernetes, String namespace, boolean deleteAppOnExit, boolean followLog) {
        String name = getName(pod);
        if (!action.equals(Watcher.Action.MODIFIED) || watchingPodName == null || !watchingPodName.equals(name)) {
            log.info("" + action + " pod " + name + " status: " + KubernetesHelper.getPodStatusText(pod) + " " + getPodCondition(pod));
        }
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

        if (!addedPods.isEmpty()) {
            List<Pod> sortedPods = new ArrayList<>(addedPods.values());
            Collections.sort(sortedPods, new Comparator<Pod>() {
                @Override
                public int compare(Pod p1, Pod p2) {
                    Date t1 = getCreationTimestamp(p1);
                    Date t2 = getCreationTimestamp(p2);
                    if (t1 != null) {
                        if (t2 == null) {
                            return 1;
                        } else {
                            return t1.compareTo(t2);
                        }
                    } else if (t2 == null) {
                        return 0;
                    }
                    return -1;
                }
            });

            Pod watchPod = sortedPods.get(sortedPods.size() - 1);
            if (isPodRunning(watchPod)) {
                watchLogOfPodName(kubernetes, namespace, deleteAppOnExit, followLog, getName(watchPod));
            }
        }
    }

    private void watchLogOfPodName(KubernetesClient kubernetes, String namespace, boolean deleteAppOnExit, boolean followLog, String name) {
        if (watchingPodName == null || !watchingPodName.equals(name)) {
            if (logWatcher != null) {
                log.info("Closing log watcher for " + watchingPodName + " as now watching " + name);
                closeLogWatcher();

            }
            ClientPodResource<Pod, DoneablePod> podResource = kubernetes.pods().inNamespace(namespace).withName(name);
            if (followLog || deleteAppOnExit) {
                watchingPodName = name;
                logWatchTerminateLatch = new CountDownLatch(1);
                logWatcher = podResource.watchLog();
                watchLog(logWatcher, name, "Failed to read log of pod " + name + ".", deleteAppOnExit);
            } else {
                String logText = podResource.getLog();
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

    private void closeLogWatcher() {
        if (logWatcher != null) {
            logWatcher.close();
            logWatcher = null;
        }
        if (logWatchTerminateLatch != null) {
            logWatchTerminateLatch.countDown();
        }
    }

    private void watchLog(final LogWatch logWatcher, String podName, final String failureMessage, boolean deleteAppOnExit) {
        final InputStream in = logWatcher.getOutput();
        final Logger log = createPodLogger();
        log.info("Tailing log of pod: " + podName);
        log.info("Press Ctrl-C to " + (deleteAppOnExit ? "undeploy the app" : "stop tailing the log"));
        log.info("");

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
                        log.info(line);
                    }
                } catch (IOException e) {
                    log.error(failureMessage + " " + e, e);
                }
            }
        };
        thread.start();
    }

    private Logger createPodLogger() {
        String prefix = "Pod> ";
        if (useColor) {
            prefix += Ansi.ansi().fg(COLOR_POD_LOG);
        }
        return new AnsiLogger(getLog(), useColor, verbose, prefix);
    }

    private String getPodCondition(Pod pod) {
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

    private FilterWatchListDeletable<Pod, PodList, Boolean, Watch, Watcher<Pod>> withSelector(ClientNonNamespaceOperation<Pod, PodList, DoneablePod, ClientPodResource<Pod, DoneablePod>> pods, LabelSelector selector) {
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
                    log.warn("Ignoring empty key in selector expression " + expression);
                    continue;
                }
                if (values == null && values.isEmpty()) {
                    log.warn("Ignoring empty values in selector expression " + expression);
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
                        log.warn("Ignoring unknown operator " + operator + " in selector expression " + expression);
                }
            }
        }
        return answer;
    }

    private LabelSelector getPodLabelSelector(HasMetadata entity) {
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

    private Date getCreationTimestamp(HasMetadata hasMetadata) {
        ObjectMeta metadata = hasMetadata.getMetadata();
        if (metadata != null) {
            return parseTimestamp(metadata.getCreationTimestamp());
        }
        return null;
    }

    private boolean isNewerPod(HasMetadata newer, HasMetadata older) {
        Date t1 = getCreationTimestamp(newer);
        Date t2 = getCreationTimestamp(older);
        if (t1 != null) {
            return t2 == null || t1.compareTo(t2) > 0;
        }
        return false;
    }

    private Date parseTimestamp(String text) {
        if (text == null) {
            return null;
        }
        return parseDate(text);
    }

}
