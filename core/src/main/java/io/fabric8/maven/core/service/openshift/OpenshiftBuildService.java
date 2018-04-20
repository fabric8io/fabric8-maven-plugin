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

package io.fabric8.maven.core.service.openshift;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.builds.Builds;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.maven.core.access.ClusterAccess;
import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.core.service.Fabric8ServiceException;
import io.fabric8.maven.core.util.IoUtil;
import io.fabric8.maven.core.util.KubernetesClientUtil;
import io.fabric8.maven.core.util.KubernetesResourceUtil;
import io.fabric8.maven.core.util.ResourceFileType;
import io.fabric8.maven.docker.assembly.ArchiverCustomizer;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigSpec;
import io.fabric8.openshift.api.model.BuildOutput;
import io.fabric8.openshift.api.model.BuildOutputBuilder;
import io.fabric8.openshift.api.model.BuildSource;
import io.fabric8.openshift.api.model.BuildStrategy;
import io.fabric8.openshift.api.model.BuildStrategyBuilder;
import io.fabric8.openshift.client.OpenShiftClient;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.archiver.tar.TarArchiver;

/**
 * @author nicola
 * @since 21/02/17
 */
public class OpenshiftBuildService implements BuildService {

    private final OpenShiftClient client;
    private final Logger log;
    private ServiceHub dockerServiceHub;
    private BuildServiceConfig config;
    private ClusterAccess clusterAccess;

    /*
     * Retry parameter
     */
    private static final int RESOURCE_CREATION_RETRIES = 5;
    private static final int RESOURCE_CREATION_RETRY_TIMEOUT_IN_MILLIS = 1000;

    public OpenshiftBuildService(OpenShiftClient client, Logger log, ServiceHub dockerServiceHub, BuildServiceConfig config) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(dockerServiceHub, "dockerServiceHub");
        Objects.requireNonNull(config, "config");

        this.client = client;
        this.log = log;
        this.dockerServiceHub = dockerServiceHub;
        this.config = config;
        this.clusterAccess = new ClusterAccess(null);
    }

    @Override
    public void build(ImageConfiguration imageConfig) throws Fabric8ServiceException {

        try {
            ImageName imageName = new ImageName(imageConfig.getName());

            File dockerTar = createBuildArchive(imageConfig);

            KubernetesListBuilder builder = new KubernetesListBuilder();

            // Check for buildconfig / imagestream and create them if necessary
            String buildName = updateOrCreateBuildConfig(config, client, builder, imageConfig);
            checkOrCreateImageStream(config, client, builder, getImageStreamName(imageName));
            applyResourceObjects(config, client, builder);

            // Start the actual build
            Build build = startBuild(client, dockerTar, buildName);

            // Wait until the build finishes
            waitForOpenShiftBuildToComplete(client, build);

            // Create a file with generated image streams
            addImageStreamToFile(getImageStreamFile(config), imageName, client);
        } catch (Fabric8ServiceException e) {
            throw e;
        } catch (Exception ex) {
            throw new Fabric8ServiceException("Unable to build the image using the OpenShift build service", ex);
        }
    }

    protected File createBuildArchive(ImageConfiguration imageConfig) throws Fabric8ServiceException {
        // Adding S2I artifacts such as environment variables in S2I mode
        ArchiverCustomizer customizer = getS2ICustomizer(imageConfig);

        try {
            // Create tar file with Docker archive
            File dockerTar;
            if (customizer != null) {
                dockerTar = dockerServiceHub.getArchiveService().createDockerBuildArchive(imageConfig, config.getDockerMojoParameters(), customizer);
            } else {
                dockerTar = dockerServiceHub.getArchiveService().createDockerBuildArchive(imageConfig, config.getDockerMojoParameters());
            }
            return dockerTar;
        } catch (MojoExecutionException e) {
            throw new Fabric8ServiceException("Unable to create the build archive", e);
        }
    }

    private ArchiverCustomizer getS2ICustomizer(ImageConfiguration imageConfiguration) throws Fabric8ServiceException {
        try {
            if (imageConfiguration.getBuildConfiguration() != null && imageConfiguration.getBuildConfiguration().getEnv() != null) {
                String fileName = IoUtil.sanitizeFileName("s2i-env-" + imageConfiguration.getName());
                final File environmentFile = new File(config.getBuildDirectory(), fileName);

                try (PrintWriter out = new PrintWriter(new FileWriter(environmentFile))) {
                    for (Map.Entry<String, String> e : imageConfiguration.getBuildConfiguration().getEnv().entrySet()) {
                        out.println(e.getKey() + "=" + e.getValue());
                    }
                }

                return new ArchiverCustomizer() {
                    @Override
                    public TarArchiver customize(TarArchiver tarArchiver) throws IOException {
                        tarArchiver.addFile(environmentFile, ".s2i/environment");
                        return tarArchiver;
                    }
                };
            } else {
                return null;
            }
        } catch (IOException e) {
            throw new Fabric8ServiceException("Unable to add environment variables to the S2I build archive", e);
        }
    }

    private File getImageStreamFile(BuildServiceConfig config) {
        return ResourceFileType.yaml.addExtension(new File(config.getBuildDirectory(), String.format("%s-is", config.getArtifactId())));
    }

    @Override
    public void postProcess(BuildServiceConfig config) {
        config.attachArtifact("is", getImageStreamFile(config));
    }

    private String updateOrCreateBuildConfig(BuildServiceConfig config, OpenShiftClient client, KubernetesListBuilder builder, ImageConfiguration imageConfig) {
        ImageName imageName = new ImageName(imageConfig.getName());
        String buildName = getS2IBuildName(config, imageName);
        String imageStreamName = getImageStreamName(imageName);
        String outputImageStreamTag = imageStreamName + ":" + (imageName.getTag() != null ? imageName.getTag() : "latest");

        BuildStrategy buildStrategyResource = createBuildStrategy(imageConfig, config.getOpenshiftBuildStrategy());
        BuildOutput buildOutput = new BuildOutputBuilder().withNewTo()
                .withKind("ImageStreamTag")
                .withName(outputImageStreamTag)
                .endTo().build();

        // Fetch exsting build config
        BuildConfig buildConfig = client.buildConfigs().withName(buildName).get();
        if (buildConfig != null) {
            // lets verify the BC
            BuildConfigSpec spec = getBuildConfigSpec(buildConfig);
            validateSourceType(buildName, spec);

            if (config.getBuildRecreateMode().isBuildConfig()) {
                // Delete and recreate afresh
                client.buildConfigs().withName(buildName).delete();
                return createBuildConfig(builder, buildName, buildStrategyResource, buildOutput);
            } else {
                // Update & return
                return updateBuildConfig(client, buildName, buildStrategyResource, buildOutput, spec);
            }
        } else {
            // Create afresh
            return createBuildConfig(builder, buildName, buildStrategyResource, buildOutput);
        }
    }

    private void validateSourceType(String buildName, BuildConfigSpec spec) {
        BuildSource source = spec.getSource();
        if (source != null) {
            String sourceType = source.getType();
            if (!Objects.equals("Binary", sourceType)) {
                log.warn("BuildServiceConfig %s is not of type: 'Binary' but is '%s' !", buildName, sourceType);
            }
        }
    }

    private BuildConfigSpec getBuildConfigSpec(BuildConfig buildConfig) {
        BuildConfigSpec spec = buildConfig.getSpec();
        if (spec == null) {
            spec = new BuildConfigSpec();
            buildConfig.setSpec(spec);
        }
        return spec;
    }

    private String createBuildConfig(KubernetesListBuilder builder, String buildName, BuildStrategy buildStrategyResource, BuildOutput buildOutput) {
        log.info("Creating BuildServiceConfig %s for %s build", buildName, buildStrategyResource.getType());
        builder.addNewBuildConfigItem()
                .withNewMetadata()
                .withName(buildName)
                .endMetadata()
                .withNewSpec()
                .withNewSource()
                .withType("Binary")
                .endSource()
                .withStrategy(buildStrategyResource)
                .withOutput(buildOutput)
                .endSpec()
                .endBuildConfigItem();
        return buildName;
    }

    private String updateBuildConfig(OpenShiftClient client, String buildName, BuildStrategy buildStrategy,
                                     BuildOutput buildOutput, BuildConfigSpec spec) {
        // lets check if the strategy or output has changed and if so lets update the BC
        // e.g. the S2I builder image or the output tag and
        if (!Objects.equals(buildStrategy, spec.getStrategy()) || !Objects.equals(buildOutput, spec.getOutput())) {
            client.buildConfigs().withName(buildName).edit()
                    .editSpec()
                    .withStrategy(buildStrategy)
                    .withOutput(buildOutput)
                    .endSpec()
                    .done();
            log.info("Updating BuildServiceConfig %s for %s strategy", buildName, buildStrategy.getType());
        } else {
            log.info("Using BuildServiceConfig %s for %s strategy", buildName, buildStrategy.getType());
        }
        return buildName;
    }

    private BuildStrategy createBuildStrategy(ImageConfiguration imageConfig, OpenShiftBuildStrategy osBuildStrategy) {
        if (osBuildStrategy == OpenShiftBuildStrategy.docker) {
            return new BuildStrategyBuilder().withType("Docker").build();
        } else if (osBuildStrategy == OpenShiftBuildStrategy.s2i) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            Map<String, String> fromExt = buildConfig.getFromExt();

            String fromName = getMapValueWithDefault(fromExt, OpenShiftBuildStrategy.SourceStrategy.name, buildConfig.getFrom());
            String fromKind = getMapValueWithDefault(fromExt, OpenShiftBuildStrategy.SourceStrategy.kind, "DockerImage");
            String fromNamespace = getMapValueWithDefault(fromExt, OpenShiftBuildStrategy.SourceStrategy.namespace, "ImageStreamTag".equals(fromKind) ? "openshift" : null);

            return new BuildStrategyBuilder()
                    .withType("Source")
                    .withNewSourceStrategy()
                    .withNewFrom()
                    .withKind(fromKind)
                    .withName(fromName)
                    .withNamespace(StringUtils.isEmpty(fromNamespace) ? null : fromNamespace)
                    .endFrom()
                    .endSourceStrategy()
                    .build();
        } else {
            throw new IllegalArgumentException("Unsupported BuildStrategy " + osBuildStrategy);
        }
    }

    private void checkOrCreateImageStream(BuildServiceConfig config, OpenShiftClient client, KubernetesListBuilder builder, String imageStreamName) {
        boolean hasImageStream = client.imageStreams().withName(imageStreamName).get() != null;
        if (hasImageStream && config.getBuildRecreateMode().isImageStream()) {
            client.imageStreams().withName(imageStreamName).delete();
            hasImageStream = false;
        }
        if (!hasImageStream) {
            log.info("Creating ImageStream %s", imageStreamName);
            builder.addNewImageStreamItem()
                    .withNewMetadata()
                        .withName(imageStreamName)
                    .endMetadata()
                    .withNewSpec()
                        .withNewLookupPolicy()
                            .withLocal(config.isS2iImageStreamLookupPolicyLocal())
                        .endLookupPolicy()
                    .endSpec()
                    .endImageStreamItem();
        } else {
            log.info("Adding to ImageStream %s", imageStreamName);
        }
    }

    private void applyResourceObjects(BuildServiceConfig config, OpenShiftClient client, KubernetesListBuilder builder) throws Exception {
        // Adding a workaround to handle intermittent Socket closed errors while
        // building on OpenShift. See https://github.com/fabric8io/fabric8-maven-plugin/issues/1133
        // for more details.
        int nTries = 0;
        boolean bResourcesCreated = false;
        Exception buildException = null;
        do {
            try {
                if (config.getEnricherTask() != null) {
                    config.getEnricherTask().execute(builder);
                }
                if (builder.hasItems()) {
                    KubernetesList k8sList = builder.build();
                    client.lists().create(k8sList);
                }
                // If we are here, it means resources got created successfully.
                bResourcesCreated = true;
            } catch (Exception aException) {
                // Handling the exception like this because we get a generic Exception from the above block.
                // Retry only when Exception is of socket closed message.
                if(aException.getMessage() != null && aException.getMessage().contains("Socket closed")) {
                    log.warn("Problem encountered while applying resource objects, retrying..");
                    buildException = aException;
                    nTries++;
                    Thread.sleep(RESOURCE_CREATION_RETRY_TIMEOUT_IN_MILLIS);
                    // Make a connection to cluster again.
                    client = clusterAccess.createDefaultClient(log);
                }
                else {
                    // The exception caught is not related to closed socket, so let's break
                    // and simply throw as it is.
                    throw new MojoExecutionException(aException.getMessage());
                }
            }
        } while (nTries < RESOURCE_CREATION_RETRIES && !bResourcesCreated);

        if (!bResourcesCreated)
            throw new MojoExecutionException(buildException.getMessage());
    }

    private Build startBuild(OpenShiftClient client, File dockerTar, String buildName) {
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
                logBuildBuildFailedDetails(client, buildName);
            }
            throw exp;
        }
    }

    private void waitForOpenShiftBuildToComplete(OpenShiftClient client, Build build) throws MojoExecutionException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch readyLatch = new CountDownLatch(1);
        final CountDownLatch logTerminateLatch = new CountDownLatch(1);
        final String buildName = KubernetesHelper.getName(build);
        final String buildPodName = buildName + "-build";

        // Don't query for logs directly, Watch over the build pod:
        Watcher<Pod> buildWatcher = getBuildWatcher(latch, readyLatch, buildPodName);
        try (Watch watcher = client.pods().withName(buildPodName).watch(buildWatcher)) {
            log.info("Waiting for build " + buildName + " to complete...");
            waitForBuildEvent(readyLatch);
            try (LogWatch logWatch = client.pods().withName(buildName + "-build").watchLog()) {
                KubernetesClientUtil.printLogsAsync(logWatch,
                        "Failed to tail build log", logTerminateLatch, log);
                // Check if the build is already finished to avoid waiting indefinitely
                Build lastBuild = client.builds().withName(buildName).get();
                String lastStatus = KubernetesResourceUtil.getBuildStatusPhase(lastBuild);
                if (Builds.isFinished(lastStatus)) {
                    log.debug("Build %s is already finished", buildName);
                    latch.countDown();
                }

                waitForBuildEvent(latch);
                logTerminateLatch.countDown();
                build = client.builds().withName(buildName).get();
                String status = KubernetesResourceUtil.getBuildStatusPhase(build);
                if (Builds.isFailed(status) || Builds.isCancelled(status)) {
                    throw new MojoExecutionException("OpenShift Build " + buildName + ": " + KubernetesResourceUtil.getBuildStatusReason(build));
                }
                log.info("Build " + buildName + " " + status);
            }
        }
    }

    private void waitForBuildEvent(CountDownLatch latch) {
        while (latch.getCount() > 0L) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    private Watcher<Pod> getBuildWatcher(final CountDownLatch latch, final CountDownLatch readyLatch, final String buildName) {
        return new Watcher<Pod>() {

            String lastStatus = "";

            @Override
            public void eventReceived(Action action, Pod buildPod) {
                String status = KubernetesHelper.getPodStatusText(buildPod);
                log.verbose("BuildWatch: Received event %s , build status: %s", action, status);
                if(KubernetesHelper.isPodReady(buildPod)) {
                    readyLatch.countDown();
                }
                if (!lastStatus.equals(status)) {
                    lastStatus = status;
                    log.verbose("Build %s status: %s", buildName, status);
                }
                if (status.equals(KubernetesResourceUtil.PodStatusPhase.SUCCESS)) {
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
                readyLatch.countDown();
            }
        };
    }

    private void logBuildBuildFailedDetails(OpenShiftClient client, String buildName) {
        try {
            BuildConfig build = client.buildConfigs().withName(buildName).get();
            ObjectReference ref = build.getSpec().getStrategy().getSourceStrategy().getFrom();
            String kind = ref.getKind();
            String name = ref.getName();

            if ("DockerImage".equals(kind)) {
                log.error("Please, ensure that the Docker image '%s' exists and is accessible by OpenShift", name);
            } else if ("ImageStreamTag".equals(kind)) {
                String namespace = ref.getNamespace();
                String namespaceInfo = "current";
                String namespaceParams = "";
                if (namespace != null && !namespace.isEmpty()) {
                    namespaceInfo = "'" + namespace + "'";
                    namespaceParams = " -n " + namespace;
                }

                log.error("Please, ensure that the ImageStream Tag '%s' exists in the %s namespace (with 'oc get is%s')", name, namespaceInfo, namespaceParams);
            }
        } catch (Exception ex) {
            log.error("Unable to get detailed information from the BuildServiceConfig: " + ex.getMessage());
        }
    }

    private void addImageStreamToFile(File imageStreamFile, ImageName imageName, OpenShiftClient client) throws MojoExecutionException {
        ImageStreamService imageStreamHandler = new ImageStreamService(client, log);
        imageStreamHandler.appendImageStreamResource(imageName, imageStreamFile);
    }

    // == Utility methods ==========================

    private String getS2IBuildName(BuildServiceConfig config, ImageName imageName) {
        return imageName.getSimpleName() + config.getS2iBuildNameSuffix();
    }

    private String getImageStreamName(ImageName name) {
        return name.getSimpleName();
    }

    private String getMapValueWithDefault(Map<String, String> map, OpenShiftBuildStrategy.SourceStrategy strategy, String defaultValue) {
        return getMapValueWithDefault(map, strategy.key(), defaultValue);
    }

    private String getMapValueWithDefault(Map<String, String> map, String field, String defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        String value = map.get(field);
        return value != null ? value : defaultValue;
    }

}
