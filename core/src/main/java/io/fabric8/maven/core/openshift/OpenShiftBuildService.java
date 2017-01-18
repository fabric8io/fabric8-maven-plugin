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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.builds.Builds;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.maven.core.util.KubernetesResourceUtil;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * @author roland
 * @since 16/01/17
 */
public class OpenShiftBuildService {

    private final OpenShiftClient client;
    private final Logger log;

    private String lastBuildStatus;

    public OpenShiftBuildService(OpenShiftClient client, Logger log) {
        this.client = client;
        this.log = log;
    }

    public Build startBuild(OpenShiftClient client, File dockerTar, String buildName) {
        log.info("Starting Build %s", buildName);
        return client.buildConfigs().withName(buildName)
                     .instantiateBinary()
                     .fromFile(dockerTar);
    }

    public void waitForOpenShiftBuildToComplete(OpenShiftClient client, Build build) throws MojoExecutionException {
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch logTerminateLatch = new CountDownLatch(1);final String buildName = KubernetesHelper.getName(build);

        final AtomicReference<Build> buildHolder = new AtomicReference<>();

        log.info("Waiting for build " + buildName + " to complete...");
        try (LogWatch logWatch = client.pods().withName(buildName + "-build").watchLog()) {
            KubernetesResourceUtil.pringLogsAsync(logWatch, "Failed to tail build log", logTerminateLatch, log);

            try (Watch watcher = client.builds().withName(buildName).watch(getBuildWatcher(latch, buildName, buildHolder))) {
                while (latch.getCount() > 0L) {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
                logTerminateLatch.countDown();
                build = buildHolder.get();
                String status = KubernetesResourceUtil.getBuildStatusPhase(build);
                if (Builds.isFailed(status) || Builds.isCancelled(status)) {
                    throw new MojoExecutionException("OpenShift Build " + buildName + " " + KubernetesResourceUtil.getBuildStatusReason(build));
                }
                log.info("Build " + buildName + " " + status);
            }
        }
    }

    public Watcher<Build> getBuildWatcher(final CountDownLatch latch, final String buildName, final AtomicReference<Build> buildHolder) {
        return new Watcher<Build>() {

                String lastStatus = "";

                @Override
                public void eventReceived(Action action, Build resource) {
                    buildHolder.set(resource);
                    String status = KubernetesResourceUtil.getBuildStatusPhase(resource);
                    log.verbose("BuildWatch: Received event %s , build status: %s",action,resource.getStatus());
                    if (!lastStatus.equals(status)) {
                        lastStatus = status;
                        log.info("Build %s status: %s", buildName, status);
                    }
                    if (Builds.isFinished(status)) {
                        latch.countDown();
                    }
                }

                @Override
                public void onClose(KubernetesClientException cause) {
                }
            };
    }

}
