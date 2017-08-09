/*
 *    Copyright (c) 2016 Red Hat, Inc.
 *
 *    Red Hat licenses this file to you under the Apache License, version
 *    2.0 (the "License"); you may not use this file except in compliance
 *    with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *    implied.  See the License for the specific language governing
 *    permissions and limitations under the License.
 */

package io.fabric8.maven.enricher.standard;

import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.maven.core.util.SecretConstants;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.apache.maven.model.Scm;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.*;

/**
 * @author yuwzho
 */
@RunWith(JMockit.class)
public class DockerRegistrySecretEnricherTest {

    @Mocked
    private EnricherContext context;

    private String dockerUrl = "docker.io";
    private String annotation = "maven.fabric8.io/dockerId";

    @Test
    public void testDockerRegistry() {
        final Settings settings = new Settings();
        settings.addServer(createBaseServer());
        BaseEnricher.applySettings(settings);

        DockerRegistrySecretEnricher enricher = new DockerRegistrySecretEnricher(context);
        KubernetesListBuilder builder = new KubernetesListBuilder();
        builder.addToSecretItems(createBaseSecret(true));

        enricher.addMissingResources(builder);

        KubernetesListBuilder expectedBuilder = new KubernetesListBuilder();
        Secret expectedSecret = createBaseSecret(false);
        expectedSecret.getData().put(SecretConstants.DOCKER_DATA_KEY,
                "eyJkb2NrZXIuaW8iOnsicGFzc3dvcmQiOiJwYXNzd29yZCIsImVtYWlsIjoiZm9vQGZvby5jb20iLCJ1c2VybmFtZSI6InVzZXJuYW1lIn19");
        expectedBuilder.addToSecretItems(expectedSecret);
        assertEquals(expectedBuilder.build(), builder.build());
    }

    @Test
    public void testDockerRegistryWithBadAnnotation() {
        final Settings settings = new Settings();
        settings.addServer(createBaseServer());
        BaseEnricher.applySettings(settings);

        DockerRegistrySecretEnricher enricher = new DockerRegistrySecretEnricher(context);
        KubernetesListBuilder builder = new KubernetesListBuilder();
        Secret secret = createBaseSecret(true);
        secret.getMetadata().getAnnotations().put(annotation, "docker1.io");
        builder.addToSecretItems(createBaseSecret(true));

        KubernetesList expected = builder.build();

        enricher.addMissingResources(builder);
        assertEquals(expected, builder.build());
    }

    private Secret createBaseSecret(boolean withAnnotation) {
        ObjectMeta meta = new ObjectMeta();
        meta.setNamespace("default");

        if (withAnnotation) {
            Map<String, String> annotations = new HashMap();
            annotations.put(annotation, dockerUrl);
            meta.setAnnotations(annotations);
        }
        Map<String, String> data = new HashMap();
        return new Secret(SecretConstants.API_VERSION, data, SecretConstants.KIND, meta, null, SecretConstants.DOCKER_CONFIG_TYPE);
    }

    private Server createBaseServer() {
        Server server = new Server();
        server.setUsername("username");
        server.setPassword("password");
        server.setId(dockerUrl);
        return server;
    }

}
