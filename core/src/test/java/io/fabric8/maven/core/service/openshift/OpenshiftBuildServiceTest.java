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

import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.WatchEvent;
import io.fabric8.maven.core.config.BuildRecreateMode;
import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.core.service.Fabric8ServiceException;
import io.fabric8.maven.core.util.WebServerEventCollector;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.MojoParameters;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildBuilder;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigBuilder;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.ImageStreamBuilder;
import io.fabric8.openshift.api.model.ImageStreamStatusBuilder;
import io.fabric8.openshift.api.model.NamedTagEventListBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.server.mock.OpenShiftMockServer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;

import static org.junit.Assert.assertTrue;


@RunWith(JMockit.class)
public class OpenshiftBuildServiceTest {

    private static final Logger LOG = LoggerFactory.getLogger(OpenshiftBuildServiceTest.class);

    private String baseDir = "target/test-files/openshift-build-service";

    private String projectName = "myapp";

    private File imageStreamFile = new File(baseDir, projectName);

    @Mocked
    private ServiceHub dockerServiceHub;

    @Mocked
    private io.fabric8.maven.docker.util.Logger logger;

    private ImageConfiguration image;

    private BuildService.BuildServiceConfig.Builder defaultConfig;

    @Before
    public void init() throws Exception {
        final File dockerFile = new File(baseDir, "Docker.tar");
        dockerFile.getParentFile().mkdirs();
        dockerFile.createNewFile();

        imageStreamFile.delete();

        new Expectations() {{
            dockerServiceHub.getArchiveService().createDockerBuildArchive(withAny(ImageConfiguration.class.cast(null)), withAny(MojoParameters.class.cast(null)));
            result = dockerFile;
        }};

        image = new ImageConfiguration.Builder()
                .name(projectName)
                .buildConfig(new BuildImageConfiguration.Builder()
                        .from(projectName)
                        .build()
                ).build();

        defaultConfig = new BuildService.BuildServiceConfig.Builder()
                .buildDirectory(baseDir)
                .buildRecreateMode(BuildRecreateMode.none)
                .s2iBuildNameSuffix("-s2i-suffix2")
                .openshiftBuildStrategy(OpenShiftBuildStrategy.s2i);
    }

    @Test
    public void testSuccessfulBuild() throws Exception {
        BuildService.BuildServiceConfig config = defaultConfig.build();
        WebServerEventCollector<OpenShiftMockServer> collector = createMockServer(config, true, 50, false, false);
        OpenShiftMockServer mockServer = collector.getMockServer();

        OpenShiftClient client = mockServer.createOpenShiftClient();
        OpenshiftBuildService service = new OpenshiftBuildService(client, logger, dockerServiceHub, config);
        service.build(image);

        // we should add a better way to assert that a certain call has been made
        assertTrue(mockServer.getRequestCount() > 8);
        collector.assertEventsRecordedInOrder("build-config-check", "new-build-config", "pushed");
        collector.assertEventsNotRecorded("patch-build-config");
        assertTrue(new File(baseDir, projectName + "-is.yml").exists());
    }

    @Test(expected = Fabric8ServiceException.class)
    public void testFailedBuild() throws Exception {
        BuildService.BuildServiceConfig config = defaultConfig.build();
        WebServerEventCollector<OpenShiftMockServer> collector = createMockServer(config, false, 50, false, false);
        OpenShiftMockServer mockServer = collector.getMockServer();

        OpenShiftClient client = mockServer.createOpenShiftClient();
        OpenshiftBuildService service = new OpenshiftBuildService(client, logger, dockerServiceHub, config);
        service.build(image);
    }

    @Test
    public void testSuccessfulSecondBuild() throws Exception {
        BuildService.BuildServiceConfig config = defaultConfig.build();
        WebServerEventCollector<OpenShiftMockServer> collector = createMockServer(config, true, 50, true, true);
        OpenShiftMockServer mockServer = collector.getMockServer();

        OpenShiftClient client = mockServer.createOpenShiftClient();
        OpenshiftBuildService service = new OpenshiftBuildService(client, logger, dockerServiceHub, config);
        service.build(image);

        assertTrue(mockServer.getRequestCount() > 8);
        collector.assertEventsRecordedInOrder("build-config-check", "patch-build-config", "pushed");
        collector.assertEventsNotRecorded("new-build-config");
        assertTrue(new File(baseDir, projectName + "-is.yml").exists());
    }

    protected WebServerEventCollector<OpenShiftMockServer> createMockServer(BuildService.BuildServiceConfig config, boolean success, long buildDelay, boolean buildConfigExists, boolean
            imageStreamExists) {
        OpenShiftMockServer mockServer = new OpenShiftMockServer(false);
        WebServerEventCollector<OpenShiftMockServer> collector = new WebServerEventCollector<>(mockServer);

        BuildConfig bc = new BuildConfigBuilder()
                .withNewMetadata()
                .withName(projectName + config.getS2iBuildNameSuffix())
                .endMetadata()
                .withNewSpec()
                .endSpec()
                .build();

        ImageStream imageStream = new ImageStreamBuilder()
                .withNewMetadata()
                .withName(projectName)
                .endMetadata()
                .withStatus(new ImageStreamStatusBuilder()
                        .addNewTagLike(new NamedTagEventListBuilder()
                                .addNewItem()
                                .withImage("abcdef0123456789")
                                .endItem()
                                .build())
                        .endTag()
                        .build())
                .build();

        KubernetesList builds = new KubernetesListBuilder().withItems(
                new BuildBuilder()
                        .withNewMetadata()
                        .withName(projectName)
                        .endMetadata()
                        .build())
                .withNewMetadata().withResourceVersion("1").endMetadata()
                .build();

        String buildStatus = success ? "Complete" : "Fail";
        Build build = new BuildBuilder()
                .withNewMetadata().withResourceVersion("2").endMetadata()
                .withNewStatus().withPhase(buildStatus).endStatus()
                .build();

        if (!buildConfigExists) {
            mockServer.expect().get().withPath("/oapi/v1/namespaces/test/buildconfigs/" + projectName + config.getS2iBuildNameSuffix()).andReply(collector.record("build-config-check").andReturn
                    (404, "")).once();
            mockServer.expect().post().withPath("/oapi/v1/namespaces/test/buildconfigs").andReply(collector.record("new-build-config").andReturn(201, bc)).once();
        } else {
            mockServer.expect().patch().withPath("/oapi/v1/namespaces/test/buildconfigs/" + projectName + config.getS2iBuildNameSuffix()).andReply(collector.record("patch-build-config").andReturn
                    (200, bc)).once();
        }
        mockServer.expect().get().withPath("/oapi/v1/namespaces/test/buildconfigs/" + projectName + config.getS2iBuildNameSuffix()).andReply(collector.record("build-config-check").andReturn(200,
                bc)).always();


        if (!imageStreamExists) {
            mockServer.expect().get().withPath("/oapi/v1/namespaces/test/imagestreams/" + projectName).andReturn(404, "").once();
        }
        mockServer.expect().get().withPath("/oapi/v1/namespaces/test/imagestreams/" + projectName).andReturn(200, imageStream).always();

        mockServer.expect().post().withPath("/oapi/v1/namespaces/test/imagestreams").andReturn(201, imageStream).once();

        mockServer.expect().post().withPath("/oapi/v1/namespaces/test/buildconfigs/" + projectName + config.getS2iBuildNameSuffix() + "/instantiatebinary?commit=").andReply(collector.record
                ("pushed").andReturn(201, imageStream)).once();

        mockServer.expect().get().withPath("/oapi/v1/namespaces/test/builds").andReply(collector.record("check-build").andReturn(200, builds)).always();
        mockServer.expect().get().withPath("/oapi/v1/namespaces/test/builds?labelSelector=openshift.io/build-config.name%3D" + projectName + config.getS2iBuildNameSuffix()).andReturn(200, builds)
                .always();

        mockServer.expect().withPath("/oapi/v1/namespaces/test/builds?fieldSelector=metadata.name%3D" + projectName + "&resourceVersion=1&watch=true")
                .andUpgradeToWebSocket().open()
                .waitFor(buildDelay)
                .andEmit(new WatchEvent(build, "MODIFIED"))
                .done().always();

        return collector;
    }

}