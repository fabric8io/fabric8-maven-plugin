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
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
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
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.fusesource.jansi.Ansi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import static io.fabric8.kubernetes.api.KubernetesHelper.getName;

/**
 * Mojo for doing everything by forking the lifecycle.
 * <p>
 * The goals fabric8:resource and fabric8:build must be bound to the proper execution phases.
 *
 * @author roland
 * @since 09/06/16
 */

@Mojo(name = "run-interactive", requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.VALIDATE)
@Execute(phase = LifecyclePhase.INSTALL)
public class RunInteractiveMojo extends DeployMojo {
    public static final Ansi.Color COLOR_POD_LOG = Ansi.Color.BLUE;

    @Parameter(property = "fabric8.deleteOnExit", defaultValue = "true")
    private boolean deleteOnExit;

    private Watch podWatcher;
    private Map<String, Pod> addedPods = new ConcurrentHashMap<>();
    private LogWatch logWatcher;
    private CountDownLatch terminateLatch = new CountDownLatch(1);
    private String watchingPodName;

    @Override
    protected void applyEntities(Controller controller, final KubernetesClient kubernetes, final String namespace, String fileName, final Set<HasMetadata> entities) throws Exception {
        super.applyEntities(controller, kubernetes, namespace, fileName, entities);

        LabelSelector selector = null;
        for (HasMetadata entity : entities) {
            selector = getPodLabelSelector(entity);
            if (selector != null) {
                break;
            }
        }
        if (selector != null) {
            if (deleteOnExit) {
                Runtime.getRuntime().addShutdownHook(new Thread("mvn fabric8:run-interactive shutdown hook") {
                    @Override
                    public void run() {
                        log.info("Terminating so removing the kubernetes resources:");
                        deleteEntities(kubernetes, namespace, entities);
                    }
                });
            }
            waitAndLogPods(kubernetes, namespace, selector);
        } else {
            log.warn("No selector in deployment so cannot watch pods!");
        }
    }

    private void waitAndLogPods(final KubernetesClient kubernetes, final String namespace, LabelSelector selector) {
        ClientNonNamespaceOperation<Pod, PodList, DoneablePod, ClientPodResource<Pod, DoneablePod>> pods = kubernetes.pods().inNamespace(namespace);
        log.info("Watching pods with selector " + selector);
        podWatcher = withSelector(pods, selector).watch(new Watcher<Pod>() {
            @Override
            public void eventReceived(Action action, Pod pod) {
                onPod(action, pod, kubernetes, namespace);
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


    private void onPod(Watcher.Action action, Pod pod, KubernetesClient kubernetes, String namespace) {
        String name = getName(pod);
        log.info("" + action + " pod " + name + " status: " + KubernetesHelper.getPodStatusText(pod));
        if (action.equals(Watcher.Action.DELETED)) {
            addedPods.remove(name);
            if (Objects.equals(watchingPodName, name)) {
                watchingPodName = null;
            }
        } else {
            if (action.equals(Watcher.Action.ADDED)) {
                // lets keep track of pods added since we did the deploy
                // so that we don't start tailing the old pods when a rolling upgrade kicks in
                addedPods.put(name, pod);
            } else if (action.equals(Watcher.Action.MODIFIED)) {
                if (KubernetesHelper.isPodRunning(pod) && addedPods.containsKey(name)) {
                    if (watchingPodName == null || !watchingPodName.equals(name)) {
                        if (logWatcher != null) {
                            log.info("Closing log watcher for " + watchingPodName + " as now watching " + name);
                            logWatcher.close();
                        }
                        watchingPodName = name;
                        logWatcher = kubernetes.pods().inNamespace(namespace).withName(name).watchLog();
                        watchLog(logWatcher, name, "Failed to read log of pod " + name + ".");
                    }
                }
            }
        }
    }

    private void watchLog(LogWatch logWatcher, String podName, final String failureMessage) {
        final InputStream in = logWatcher.getOutput();
        String prefix = "Pod> ";
        if (useColor) {
            prefix += Ansi.ansi().fg(COLOR_POD_LOG);
        }
        final Logger log = new AnsiLogger(getLog(), useColor, verbose, prefix);
        log.info("Tailing log of pod: " + podName);

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
                        log.info(line);
                    }
                } catch (IOException e) {
                    log.error(failureMessage + " " + e, e);
                }
            }
        };
        thread.start();
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
}
