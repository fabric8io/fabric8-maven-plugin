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
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.PodStatusType;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.maven.core.util.KubernetesClientUtil;
import io.fabric8.maven.core.util.KubernetesResourceUtil;
import io.fabric8.maven.core.util.ProcessUtil;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.utils.Strings;

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

    public PortForwardService(ClientToolsService clientToolsService, Logger log, KubernetesClient kubernetes) {
        this.clientToolsService = clientToolsService;
        this.log = log;
        this.kubernetes = kubernetes;
    }

    /**
     * Forwards a port to the newest pod matching the given selector.
     * If another pod is created, it forwards connections to the new pod once it's ready.
     */
    public Closeable forwardPortAsync(final Logger externalProcessLogger, final LabelSelector podSelector, final int remotePort, final int localPort) throws Fabric8ServiceException {

        final Lock monitor = new ReentrantLock(true);
        final Condition podChanged = monitor.newCondition();
        final LinkedList<Pod> forwardedPods = new LinkedList<>();

        final Thread forwarderThread = new Thread() {
            @Override
            public void run() {

                Pod currentPod = null;
                Closeable currentPortForward = null;

                try {
                    monitor.lock();

                    while (true) {
                        if (forwardedPods.isEmpty() || podEquals(currentPod, forwardedPods.getLast())) {
                            podChanged.await();
                        } else {
                            Pod nextPod = forwardedPods.getLast(); // may be null
                            monitor.unlock();

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

                            monitor.lock();
                        }

                    }

                } catch (InterruptedException e) {
                    // end
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

        // Adding the current pod if present to the list
        Pod newPod = getNewestPod(podSelector);
        if (newPod != null) {
            forwardedPods.add(newPod);
        }


        final Watch watch = kubernetes.pods().watch(new Watcher<Pod>() {

            @Override
            public void eventReceived(Action action, Pod pod) {
                monitor.lock();
                try {
                    Pod newPod = getNewestPod(podSelector); // may be null
                    forwardedPods.add(newPod);
                    podChanged.signal();
                } finally {
                    monitor.unlock();
                }
            }

            @Override
            public void onClose(KubernetesClientException e) {
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
                } catch (Exception e) {}
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

        Pod targetPod = null;
        PodList list = pods.list();
        if (list != null) {
            List<Pod> items = list.getItems();
            if (items != null) {
                for (Pod pod : items) {
                    PodStatusType status = KubernetesHelper.getPodStatus(pod);
                    switch (status) {
                    case WAIT:
                    case OK:
                        if (targetPod == null || (KubernetesHelper.isPodReady(pod) && KubernetesResourceUtil.isNewerResource(pod, targetPod))) {
                            targetPod = pod;
                        }
                        break;

                    case ERROR:
                    default:
                        continue;
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
        File command = clientToolsService.getKubeCtlExecutable();
        log.info("Port forwarding to port " + remotePort + " on pod " + pod + " using command " + command);

        List<String> args = new ArrayList<>();
        args.add("port-forward");
        args.add(pod);
        args.add(localPort + ":" + remotePort);

        String commandLine = command + " " + Strings.join(args, " ");
        log.verbose("Executing command " + commandLine);
        try {
            return ProcessUtil.runAsyncCommand(externalProcessLogger, command, args, true);
        } catch (IOException e) {
            throw new Fabric8ServiceException("Error while executing the port-forward command", e);
        }
    }
}
