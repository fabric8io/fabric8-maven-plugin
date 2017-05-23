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
package io.fabric8.maven.core.service;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.maven.core.util.ProcessUtil;
import io.fabric8.maven.core.util.kubernetes.KubernetesClientUtil;
import io.fabric8.maven.core.util.kubernetes.KubernetesHelper;
import io.fabric8.maven.core.util.kubernetes.KubernetesResourceUtil;
import io.fabric8.maven.core.util.kubernetes.OpenshiftHelper;
import io.fabric8.maven.docker.util.Logger;
import org.apache.commons.lang3.StringUtils;

/**
 * A service for forwarding connections to remote pods.
 *
 * @author nicola
 * @since 28/03/2017
 */
public class PortForwardService {

    private ClientToolsService clientToolsService;

    private Logger log;

    private KubernetesClient kubernetes;

    public PortForwardService(KubernetesClient kubernetes, Logger log) {
        this.clientToolsService = new ClientToolsService(log);
        this.log = Objects.requireNonNull(log, "log");
        this.kubernetes = Objects.requireNonNull(kubernetes, "kubernetes");
    }

    /**
     * Forwards a port to the newest pod matching the given selector.
     * If another pod is created, it forwards connections to the new pod once it's ready.
     */
    public Closeable forwardPortAsync(final Logger externalProcessLogger, final LabelSelector podSelector, final int remotePort, final int localPort) throws Fabric8ServiceException {

        final Lock monitor = new ReentrantLock(true);
        final Condition podChanged = monitor.newCondition();
        final Pod[] nextForwardedPod = new Pod[1];

        final Thread forwarderThread = new Thread() {
            @Override
            public void run() {

                Pod currentPod = null;
                Closeable currentPortForward = null;

                try {
                    monitor.lock();

                    while (true) {
                        if (podEquals(currentPod, nextForwardedPod[0])) {
                            podChanged.await();
                        } else {
                            Pod nextPod = nextForwardedPod[0]; // may be null
                            try {
                                monitor.unlock();
                                // out of critical section

                                if (currentPortForward != null) {
                                    log.info("Closing port-forward from pod %s", KubernetesHelper.getName(currentPod));
                                    currentPortForward.close();
                                    currentPortForward = null;
                                }

                                if (nextPod != null) {
                                    log.info("Starting port-forward to pod %s", KubernetesHelper.getName(nextPod));
                                    currentPortForward = forwardPortAsync(externalProcessLogger, KubernetesHelper.getName(nextPod), remotePort, localPort);
                                } else {
                                    log.info("Waiting for a pod to become ready before starting port-forward");
                                }
                                currentPod = nextPod;
                            } finally {
                                monitor.lock();
                            }
                        }

                    }

                } catch (InterruptedException e) {
                    log.debug("Port-forwarding thread interrupted", e);
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.warn("Error while port-forwarding to pod", e);
                } finally {
                    monitor.unlock();

                    if (currentPortForward != null) {
                        try {
                            currentPortForward.close();
                        } catch (Exception e) {}
                    }
                }
            }
        };

        // Switching forward to the current pod if present
        Pod newPod = getNewestPod(podSelector);
        nextForwardedPod[0] = newPod;

        final Watch watch = KubernetesClientUtil.withSelector(kubernetes.pods(), podSelector, log).watch(new Watcher<Pod>() {

            @Override
            public void eventReceived(Action action, Pod pod) {
                monitor.lock();
                try {
                    List<Pod> candidatePods;
                    if (nextForwardedPod[0] != null) {
                        candidatePods = new LinkedList<>();
                        candidatePods.add(nextForwardedPod[0]);
                        candidatePods.add(pod);
                    } else {
                        candidatePods = Collections.singletonList(pod);
                    }
                    Pod newPod = getNewestPod(candidatePods); // may be null
                    if (!podEquals(nextForwardedPod[0], newPod)) {
                        nextForwardedPod[0] = newPod;
                        podChanged.signal();
                    }
                } finally {
                    monitor.unlock();
                }
            }

            @Override
            public void onClose(KubernetesClientException e) {
                // don't care
            }
        });

        forwarderThread.start();

        final Closeable handle = new Closeable() {
            @Override
            public void close() throws IOException {
                try {
                    watch.close();
                } catch (Exception e) {}
                try {
                    forwarderThread.interrupt();
                    forwarderThread.join(15000);
                } catch (Exception e) {}
            }
        };
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    handle.close();
                } catch (Exception e) {
                    // suppress
                }
            }
        });

        return handle;
    }

    private boolean podEquals(Pod pod1, Pod pod2) {
        if (pod1 == pod2) {
            return true;
        }
        if (pod1 == null || pod2 == null) {
            return false;
        }
        return KubernetesHelper.getName(pod1).equals(KubernetesHelper.getName(pod2));
    }

    private Pod getNewestPod(LabelSelector selector) {
        FilterWatchListDeletable<Pod, PodList, Boolean, Watch, Watcher<Pod>> pods =
                KubernetesClientUtil.withSelector(kubernetes.pods(), selector, log);

        PodList list = pods.list();
        if (list != null) {
            List<Pod> items = list.getItems();
            return getNewestPod(items);
        }
        return null;
    }

    private Pod getNewestPod(List<Pod> items) {
        Pod targetPod = null;
        if (items != null) {
            for (Pod pod : items) {
                if (KubernetesHelper.isPodWaiting(pod) || KubernetesHelper.isPodRunning(pod)) {
                    if (targetPod == null || (KubernetesHelper.isPodReady(pod) && KubernetesResourceUtil.isNewerResource(pod, targetPod))) {
                        targetPod = pod;
                    }
                }
            }
        }
        return targetPod;
    }

    public void forwardPort(Logger externalProcessLogger, String pod, int remotePort, int localPort) throws Fabric8ServiceException {
        forwardPortAsync(externalProcessLogger, pod, remotePort, localPort).await();
    }

    public ProcessUtil.ProcessExecutionContext forwardPortAsync(Logger externalProcessLogger, String pod, int remotePort, int localPort) throws Fabric8ServiceException {
        File command = clientToolsService.getKubeCtlExecutable(OpenshiftHelper.isOpenShiftClient(kubernetes));
        log.info("Port forwarding to port " + remotePort + " on pod " + pod + " using command " + command);

        List<String> args = new ArrayList<>();
        args.add("port-forward");
        args.add(pod);
        args.add(localPort + ":" + remotePort);

        String commandLine = command + " " + StringUtils.join(args, " ");
        log.verbose("Executing command " + commandLine);
        try {
            return ProcessUtil.runAsyncCommand(externalProcessLogger, command, args, true, false);
        } catch (IOException e) {
            throw new Fabric8ServiceException("Error while executing the port-forward command", e);
        }
    }
}
