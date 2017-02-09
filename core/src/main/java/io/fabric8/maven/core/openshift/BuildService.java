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

package io.fabric8.maven.core.openshift;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.builds.Builds;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.maven.core.util.KubernetesClientUtil;
import io.fabric8.maven.core.util.KubernetesResourceUtil;
import io.fabric8.openshift.api.model.*;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * @author roland
 * @since 16/01/17
 */
public class BuildService {

    private final OpenShiftClient client;
    private final Logger log;

    private String lastBuildStatus;

    public BuildService(OpenShiftClient client, Logger log) {
        this.client = client;
        this.log = log;
    }

    public Build startBuild(OpenShiftClient client, File dockerTar, String buildName) {
        log.info("Starting Build %s", buildName);
        try {
            return client.buildConfigs().withName(buildName)
                         .instantiateBinary()
                         .fromFile(dockerTar);
        } catch (KubernetesClientException exp) {
            Status status = exp.getStatus();
            if (status != null) {
                log.error("OpenShift Error: [%d %s] [%s] %s", status.getCode(), status.getStatus(), status.getReason(), status.getMessage());
            }
            if (exp.getCause() instanceof IOException && exp.getCause().getMessage().contains("Stream Closed")) {
                log.error("Build for %s failed: %s", buildName, exp.getCause().getMessage());
                log.error("If you are refering to an ImageStream as S2I builder image, please ensure that this ImageStream exists (with 'oc get is')");
            }
            throw exp;
        }
    }

    public void waitForOpenShiftBuildToComplete(OpenShiftClient client, Build build) throws MojoExecutionException {
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch logTerminateLatch = new CountDownLatch(1);
        final String buildName = KubernetesHelper.getName(build);

        final AtomicReference<Build> buildHolder = new AtomicReference<>();

        log.info("Waiting for build " + buildName + " to complete...");
        try (LogWatch logWatch = client.pods().withName(buildName + "-build").watchLog()) {
            KubernetesClientUtil.printLogsAsync(logWatch,
                                                  "Failed to tail build log", logTerminateLatch, log);
            Watcher<Build> buildWatcher = getBuildWatcher(latch, buildName, buildHolder);
            try (Watch watcher = client.builds().withName(buildName).watch(buildWatcher)) {
                waitUntilBuildFinished(latch);
                logTerminateLatch.countDown();
                build = buildHolder.get();
                String status = KubernetesResourceUtil.getBuildStatusPhase(build);
                if (Builds.isFailed(status) || Builds.isCancelled(status)) {
                    throw new MojoExecutionException("OpenShift Build " + buildName + ": " + KubernetesResourceUtil.getBuildStatusReason(build));
                }
                log.info("Build " + buildName + " " + status);
            }
        }
    }

    public void waitUntilBuildFinished(CountDownLatch latch) {
        while (latch.getCount() > 0L) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    public Watcher<Build> getBuildWatcher(final CountDownLatch latch, final String buildName, final AtomicReference<Build> buildHolder) {
        return new Watcher<Build>() {

                String lastStatus = "";

                @Override
                public void eventReceived(Action action, Build build) {
                    buildHolder.set(build);
                    String status = KubernetesResourceUtil.getBuildStatusPhase(build);
                    log.verbose("BuildWatch: Received event %s , build status: %s", action, build.getStatus());
                    if (!lastStatus.equals(status)) {
                        lastStatus = status;
                        log.verbose("Build %s status: %s", buildName, status);
                    }
                    if (Builds.isFinished(status)) {
                        latch.countDown();
                    }
                }

                @Override
                public void onClose(KubernetesClientException cause) {
                    if (cause != null) {
                        log.error("Error while watching for build to finish: %s [%d]",
                                  cause.getMessage(), cause.getCode());
                        Status status = cause.getStatus();
                        if (status != null) {
                            log.error("%s [%s]", status.getReason(), status.getStatus());
                        }
                    }
                    latch.countDown();
                }
            };
    }

}
