/**
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
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.LocalObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.core.service.Fabric8ServiceException;
import io.fabric8.maven.core.util.IoUtil;
import io.fabric8.maven.core.util.ResourceFileType;
import io.fabric8.maven.core.util.kubernetes.KubernetesClientUtil;
import io.fabric8.maven.core.util.kubernetes.KubernetesHelper;
import io.fabric8.maven.core.util.kubernetes.KubernetesResourceUtil;
import io.fabric8.maven.core.util.kubernetes.OpenshiftHelper;
import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.assembly.ArchiverCustomizer;
import io.fabric8.maven.docker.assembly.DockerAssemblyManager;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.RegistryService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.AuthConfigFactory;
import io.fabric8.maven.docker.util.DockerFileUtil;
import io.fabric8.maven.docker.util.EnvUtil;
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
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * @author nicola
 * @since 21/02/17
 */
public class OpenshiftBuildService implements BuildService {

    private final OpenShiftClient client;
    private final Logger log;
    private ServiceHub dockerServiceHub;
    private BuildServiceConfig config;
    private RegistryService.RegistryConfig registryConfig;
    private AuthConfigFactory authConfigFactory;


    public OpenshiftBuildService(OpenShiftClient client, Logger log, ServiceHub dockerServiceHub, BuildServiceConfig config) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(dockerServiceHub, "dockerServiceHub");
        Objects.requireNonNull(config, "config");

        this.client = client;
        this.log = log;
        this.dockerServiceHub = dockerServiceHub;
        this.config = config;
    }

    @Override
    public void build(ImageConfiguration imageConfig) throws Fabric8ServiceException {
        String buildName = null;
        try {
            ImageName imageName = new ImageName(imageConfig.getName());

            File dockerTar = createBuildArchive(imageConfig);

            KubernetesListBuilder builder = new KubernetesListBuilder();

            // Check for buildconfig / imagestream / pullSecret and create them if necessary
            String openshiftPullSecret = config.getOpenshiftPullSecret();
            Boolean usePullSecret = checkOrCreatePullSecret(config, client, builder, openshiftPullSecret, imageConfig);
            if (usePullSecret) {
                buildName = updateOrCreateBuildConfig(config, client, builder, imageConfig, openshiftPullSecret);
            } else {
                buildName = updateOrCreateBuildConfig(config, client, builder, imageConfig, null);
            }

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
            // Log additional details in case of any IOException
            if (ex != null && ex.getCause() instanceof IOException) {
                log.error("Build for %s failed: %s", buildName, ex.getCause().getMessage());
                logBuildFailure(client, buildName);
            } else {
                throw new Fabric8ServiceException("Unable to build the image using the OpenShift build service", ex);
            }
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

                return tarArchiver -> {
                    tarArchiver.addFile(environmentFile, ".s2i/environment");
                    return tarArchiver;
                };
            } else {
                return null;
            }
        } catch (IOException e) {
            throw new Fabric8ServiceException("Unable to add environment variables to the S2I build archive", e);
        }
    }

    private File getImageStreamFile(BuildServiceConfig config) {
        return ResourceFileType.yaml.addExtensionIfMissing(new File(config.getBuildDirectory(), String.format("%s-is", config.getArtifactId())));
    }

    @Override
    public void postProcess(BuildServiceConfig config) {
        config.attachArtifact("is", getImageStreamFile(config));
    }

    private String updateOrCreateBuildConfig(BuildServiceConfig config, OpenShiftClient client, KubernetesListBuilder builder, ImageConfiguration imageConfig, String openshiftPullSecret) {
        ImageName imageName = new ImageName(imageConfig.getName());
        String buildName = getS2IBuildName(config, imageName);
        String imageStreamName = getImageStreamName(imageName);
        String outputImageStreamTag = imageStreamName + ":" + (imageName.getTag() != null ? imageName.getTag() : "latest");

        BuildStrategy buildStrategyResource = createBuildStrategy(imageConfig, config.getOpenshiftBuildStrategy(), openshiftPullSecret);
        BuildOutput buildOutput = new BuildOutputBuilder().withNewTo()
                .withKind("ImageStreamTag")
                .withName(outputImageStreamTag)
                .endTo().build();

        // Fetch existing build config
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

    private BuildStrategy createBuildStrategy(ImageConfiguration imageConfig, OpenShiftBuildStrategy osBuildStrategy, String openshiftPullSecret) {
        if (osBuildStrategy == OpenShiftBuildStrategy.docker) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            Map<String, String> fromExt = buildConfig.getFromExt();
            String fromName, fromKind, fromNamespace;

            if(buildConfig.isDockerFileMode()) {
                fromName = extractBaseFromDockerfile(buildConfig, config.getDockerBuildContext());
            } else {
                fromName = getMapValueWithDefault(fromExt, OpenShiftBuildStrategy.SourceStrategy.name, buildConfig.getFrom());
            }
            fromKind = getMapValueWithDefault(fromExt, OpenShiftBuildStrategy.SourceStrategy.kind, "DockerImage");
            fromNamespace = getMapValueWithDefault(fromExt, OpenShiftBuildStrategy.SourceStrategy.namespace, "ImageStreamTag".equals(fromKind) ? "openshift" : null);

            BuildStrategy buildStrategy = new BuildStrategyBuilder()
                    .withType("Docker")
                    .withNewDockerStrategy()
                    .withNewFrom()
                    .withKind(fromKind)
                    .withName(fromName)
                    .withNamespace(StringUtils.isEmpty(fromNamespace) ? null : fromNamespace)
                    .endFrom()
                    .withNoCache(checkForNocache(imageConfig))
                    .endDockerStrategy().build();
            
            if (openshiftPullSecret != null) {
                buildStrategy.getDockerStrategy().setPullSecret(new LocalObjectReferenceBuilder()
                .withName(openshiftPullSecret)
                .build());
            }

            return buildStrategy;
        } else if (osBuildStrategy == OpenShiftBuildStrategy.s2i) {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            Map<String, String> fromExt = buildConfig.getFromExt();
            String fromName, fromKind, fromNamespace;

            if(buildConfig.isDockerFileMode()) {
                fromName = extractBaseFromDockerfile(buildConfig, config.getDockerBuildContext());
            } else {
                fromName = getMapValueWithDefault(fromExt, OpenShiftBuildStrategy.SourceStrategy.name, buildConfig.getFrom());
            }
            fromKind = getMapValueWithDefault(fromExt, OpenShiftBuildStrategy.SourceStrategy.kind, "DockerImage");
            fromNamespace = getMapValueWithDefault(fromExt, OpenShiftBuildStrategy.SourceStrategy.namespace, "ImageStreamTag".equals(fromKind) ? "openshift" : null);

            BuildStrategy buildStrategy = new BuildStrategyBuilder()
                    .withType("Source")
                    .withNewSourceStrategy()
                    .withNewFrom()
                    .withKind(fromKind)
                    .withName(fromName)
                    .withNamespace(StringUtils.isEmpty(fromNamespace) ? null : fromNamespace)
                    .endFrom()
                    .withForcePull(config.isForcePullEnabled())
                    .endSourceStrategy()
                    .build();
            if (openshiftPullSecret != null) {
                buildStrategy.getSourceStrategy().setPullSecret(new LocalObjectReferenceBuilder()
                .withName(openshiftPullSecret)
                .build());
            }

            return buildStrategy;

        } else {
            throw new IllegalArgumentException("Unsupported BuildStrategy " + osBuildStrategy);
        }
    }

    private Boolean checkForNocache(ImageConfiguration imageConfig) {
        String nocache = System.getProperty("docker.nocache");
        if (nocache != null) {
            return nocache.length() == 0 || Boolean.valueOf(nocache);
        } else {
            BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
            return buildConfig.getNoCache();
        }
    }

    private Boolean checkOrCreatePullSecret(BuildServiceConfig config, OpenShiftClient client, KubernetesListBuilder builder, String pullSecretName, ImageConfiguration imageConfig)
            throws MojoExecutionException, UnsupportedEncodingException {
        io.fabric8.maven.docker.service.BuildService.BuildContext dockerBuildContext = config.getDockerBuildContext();
        BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();

        String fromImage;
        if (buildConfig.isDockerFileMode()) {
            fromImage = extractBaseFromDockerfile(buildConfig, dockerBuildContext);
        } else {
            fromImage = extractBaseFromConfiguration(buildConfig);
        }

        String pullRegistry = EnvUtil.firstRegistryOf(new ImageName(fromImage).getRegistry(), dockerBuildContext.getRegistryConfig().getRegistry(), dockerBuildContext.getRegistryConfig().getRegistry());

        if (pullRegistry != null) {
            RegistryService.RegistryConfig registryConfig = dockerBuildContext.getRegistryConfig();
            AuthConfig authConfig = registryConfig.getAuthConfigFactory().createAuthConfig(false, registryConfig.isSkipExtendedAuth(), registryConfig.getAuthConfig(),
                    registryConfig.getSettings(), null, pullRegistry);

            if (authConfig != null) {

                JsonObject auths = new JsonObject();
                JsonObject auth = new JsonObject();
                JsonObject item = new JsonObject();

                String authString = authConfig.getUsername() + ":" + authConfig.getPassword();
                item.add("auth", new JsonPrimitive(Base64.encodeBase64String(authString.getBytes("UTF-8"))));
                auth.add(pullRegistry, item);
                auths.add("auths", auth);

                String credentials = Base64.encodeBase64String(auths.toString().getBytes("UTF-8"));

                Map<String, String> data = new HashMap<>();
                data.put(".dockerconfigjson", credentials);

                boolean hasPullSecret = client.secrets().withName(pullSecretName).get() != null;

                if (!hasPullSecret) {
                    log.info("Creating Secret %s", hasPullSecret);
                    builder.addNewSecretItem()
                            .withNewMetadata()
                            .withName(pullSecretName)
                            .endMetadata()
                            .withData(data)
                            .withType("kubernetes.io/dockerconfigjson")
                            .endSecretItem();
                } else {
                    log.info("Adding to Secret %s", pullSecretName);
                    return updateSecret(client, pullSecretName, data);
                }

                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    private boolean updateSecret(OpenShiftClient client, String pullSecretName, Map<String, String> data) {
        if (!Objects.equals(data, client.secrets().withName(pullSecretName).get().getData())) {
            client.secrets().withName(pullSecretName).edit()
                    .editMetadata()
                    .withName(pullSecretName)
                    .endMetadata()
                    .withData(data)
                    .withType("kubernetes.io/dockerconfigjson")
                    .done();
            log.info("Updating Secret %s", pullSecretName);
        } else {
            log.info("Using Secret %s", pullSecretName);
        }
        return true;
    }


    private String extractBaseFromConfiguration(BuildImageConfiguration buildConfig) {
        String fromImage;
        fromImage = buildConfig.getFrom();
        if (fromImage == null) {
            AssemblyConfiguration assemblyConfig = buildConfig.getAssemblyConfiguration();
            if (assemblyConfig == null) {
                fromImage = DockerAssemblyManager.DEFAULT_DATA_BASE_IMAGE;
            }
        }
        return fromImage;
    }

    private String extractBaseFromDockerfile(BuildImageConfiguration buildConfig, io.fabric8.maven.docker.service.BuildService.BuildContext buildContext) {
        String fromImage;
        try {
            File fullDockerFilePath = buildConfig.getAbsoluteDockerFilePath(buildContext.getMojoParameters());
            fromImage = DockerFileUtil.extractBaseImages(
                    fullDockerFilePath,
                    DockerFileUtil.createInterpolator(buildContext.getMojoParameters(), buildConfig.getFilter())).stream().findFirst().orElse(null);
        } catch (IOException e) {
            // Cant extract base image, so we wont try an auto pull. An error will occur later anyway when
            // building the image, so we are passive here.
            fromImage = null;
        }
        return fromImage;
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
        if (config.getEnricherTask() != null) {
            config.getEnricherTask().execute(builder);
        }

        if (builder.hasItems()) {
            KubernetesList k8sList = builder.build();
            client.lists().create(k8sList);
        }
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
                logBuildFailedDetails(client, buildName);
            }
            throw exp;
        }
    }

    private void waitForOpenShiftBuildToComplete(OpenShiftClient client, Build build) throws MojoExecutionException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch logTerminateLatch = new CountDownLatch(1);
        final String buildName = KubernetesHelper.getName(build);

        final AtomicReference<Build> buildHolder = new AtomicReference<>();

        // Don't query for logs directly, Watch over the build pod:
        waitUntilPodIsReady(buildName + "-build", 120, log);
        log.info("Waiting for build " + buildName + " to complete...");
        try (LogWatch logWatch = client.pods().withName(buildName + "-build").watchLog()) {
            KubernetesClientUtil.printLogsAsync(logWatch,
                    "Failed to tail build log", logTerminateLatch, log);
            Watcher<Build> buildWatcher = getBuildWatcher(latch, buildName, buildHolder);
            try (Watch watcher = client.builds().withName(buildName).watch(buildWatcher)) {
                // Check if the build is already finished to avoid waiting indefinitely
                Build lastBuild = client.builds().withName(buildName).get();
                if (OpenshiftHelper.isFinished(KubernetesResourceUtil.getBuildStatusPhase(lastBuild))) {
                    log.debug("Build %s is already finished", buildName);
                    buildHolder.set(lastBuild);
                    latch.countDown();
                }

                waitUntilBuildFinished(latch);
                logTerminateLatch.countDown();

                build = buildHolder.get();
                if (build == null) {
                    log.debug("Build watcher on %s was closed prematurely", buildName);
                    build = client.builds().withName(buildName).get();
                }
                String status = KubernetesResourceUtil.getBuildStatusPhase(build);
                if (OpenshiftHelper.isFailed(status) || OpenshiftHelper.isCancelled(status)) {
                    throw new MojoExecutionException("OpenShift Build " + buildName + " failed: " + KubernetesResourceUtil.getBuildStatusReason(build));
                }

                if (!OpenshiftHelper.isFinished(status)) {
                    log.warn("Could not wait for the completion of build %s. It may be  may be still running (status=%s)", buildName, status);
                } else {
                    log.info("Build %s in status %s", buildName, status);
                }
            }
        }
    }

    /**
     * A Simple utility function to watch over pod until it gets ready
     *
     * @param podName Name of the pod
     * @param nAwaitTimeout Time in seconds upto which pod must be watched
     * @param log Logger object
     * @throws InterruptedException
     */
    private void waitUntilPodIsReady(String podName, int nAwaitTimeout, final Logger log) throws InterruptedException {
        final CountDownLatch readyLatch = new CountDownLatch(1);
        try (Watch watch = client.pods().withName(podName).watch(new Watcher<Pod>() {
            @Override
            public void eventReceived(Action action, Pod aPod) {
                if(KubernetesHelper.isPodReady(aPod)) {
                    readyLatch.countDown();
                }
            }

            @Override
            public void onClose(KubernetesClientException e) {
                // Ignore
            }
        })) {
            readyLatch.await(nAwaitTimeout, TimeUnit.SECONDS);
        } catch (KubernetesClientException | InterruptedException e) {
            log.error("Could not watch pod", e);
        }
    }

    private void waitUntilBuildFinished(CountDownLatch latch) {
        while (latch.getCount() > 0L) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    private Watcher<Build> getBuildWatcher(final CountDownLatch latch, final String buildName, final AtomicReference<Build> buildHolder) {
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
                if (OpenshiftHelper.isFinished(status)) {
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

    private void logBuildFailedDetails(OpenShiftClient client, String buildName) {
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

    private void logBuildFailure(OpenShiftClient client, String buildName) throws Fabric8ServiceException {
        try {
            List<Build> builds = client.builds().inNamespace(client.getNamespace()).list().getItems();
            for(Build build : builds) {
                if(build.getMetadata().getName().contains(buildName)) {
                    log.error(build.getMetadata().getName() + "\t" + "\t" + build.getStatus().getReason() + "\t" + build.getStatus().getMessage());
                    throw new Fabric8ServiceException("Unable to build the image using the OpenShift build service", new KubernetesClientException(build.getStatus().getReason() + " " + build.getStatus().getMessage()));
                }
            }

            log.error("Also, check cluster events via `oc get events` to see what could have possibly gone wrong");
        } catch (KubernetesClientException clientException) {
            Status status = clientException.getStatus();
            if (status != null)
                log.error("OpenShift Error: [%d] %s", status.getCode(), status.getMessage());
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
