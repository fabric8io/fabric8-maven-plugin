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

package io.fabric8.maven.core.access;

import io.fabric8.kubernetes.api.model.RootPaths;
import io.fabric8.kubernetes.client.Client;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.server.mock.OpenShiftMockServer;
import mockit.integration.junit4.JMockit;
import org.junit.Test;
import org.junit.runner.RunWith;
import mockit.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(JMockit.class)
public class ClusterAccessTest {

    @Mocked
    private Logger logger;

    private PlatformMode mode;

    private List<String> paths = new ArrayList<String>() ;

    OpenShiftMockServer mockServer = new OpenShiftMockServer(false);
    OpenShiftClient client = mockServer.createOpenShiftClient();

    @Test
    public void openshiftPlatformModeTest() throws Exception {

        paths.add("/oapi");
        paths.add("/oapi/v1");

        RootPaths rootpaths = new RootPaths();

        rootpaths.setPaths(paths);

        mockServer.expect().get().withPath("/" ).andReturn(200, rootpaths).always();

        ClusterAccess clusterAccess = new ClusterAccess(null, client);

        mode = clusterAccess.resolvePlatformMode(PlatformMode.openshift, logger);
        assertEquals(PlatformMode.openshift, mode);

        mode = clusterAccess.resolvePlatformMode(PlatformMode.DEFAULT, logger);
        assertEquals(PlatformMode.openshift, mode);

        mode = clusterAccess.resolvePlatformMode(null, logger);
        assertEquals(PlatformMode.openshift, mode);
    }

    @Test
    public void kubernetesPlatformModeTest() throws Exception {

        RootPaths rootpaths = new RootPaths();

        rootpaths.setPaths(paths);

        mockServer.expect().get().withPath("/" ).andReturn(200, rootpaths).always();

        ClusterAccess clusterAccess = new ClusterAccess(null, client);

        mode = clusterAccess.resolvePlatformMode(PlatformMode.kubernetes, logger);
        assertEquals(PlatformMode.kubernetes, mode);

        mode = clusterAccess.resolvePlatformMode(PlatformMode.DEFAULT, logger);
        assertEquals(PlatformMode.kubernetes, mode);

        mode = clusterAccess.resolvePlatformMode(null, logger);
        assertEquals(PlatformMode.kubernetes, mode);
    }

    @Test
    public void createClientTestOpenshift() throws Exception {

        paths.add("/oapi");
        paths.add("/oapi/v1");

        RootPaths rootpaths = new RootPaths();

        rootpaths.setPaths(paths);

        mockServer.expect().get().withPath("/" ).andReturn(200, rootpaths).always();

        ClusterAccess clusterAccess = new ClusterAccess(null, client);

        Client outputClient = clusterAccess.createDefaultClient(logger);
        assertTrue(outputClient instanceof OpenShiftClient);

    }

    @Test
    public void createClientTestKubernetes() throws Exception {

        RootPaths rootpaths = new RootPaths();

        rootpaths.setPaths(paths);

        mockServer.expect().get().withPath("/" ).andReturn(200, rootpaths).always();

        ClusterAccess clusterAccess = new ClusterAccess(null, client);

        Client outputClient = clusterAccess.createDefaultClient(logger);
        assertTrue(outputClient instanceof KubernetesClient);  }

}