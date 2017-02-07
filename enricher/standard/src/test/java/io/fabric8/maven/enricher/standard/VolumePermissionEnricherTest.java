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
package io.fabric8.maven.enricher.standard;

import java.util.Collections;
import java.util.TreeMap;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.util.InitContainerHandler;
import io.fabric8.utils.Strings;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(JMockit.class)
public class VolumePermissionEnricherTest {

    @Mocked
    private EnricherContext context;

    // *******************************
    // Tests
    // *******************************

    private static final class TestConfig {
        private final String permission;
        private final String initContainerName;
        private final String[] volumeNames;

        private TestConfig(String permission, String initContainerName, String... volumeNames) {
            this.permission = permission;
            this.initContainerName = initContainerName;
            this.volumeNames = volumeNames;
        }
    }

    @Test
    public void alreadyExistingInitContainer(@Mocked final ProcessorConfig config) throws Exception {
        new Expectations() {{
            context.getConfig(); result = config;
        }};

        PodTemplateBuilder ptb = createEmptyPodTemplate();
        addVolume(ptb, "VolumeA");

        JSONArray initContainers = new JSONArray();
        JSONObject initContainer = new JSONObject();
        initContainer.put("name", VolumePermissionEnricher.ENRICHER_NAME);
        initContainer.put("mountPath", "blub");
        initContainers.put(initContainer);
        ptb.editTemplate().editMetadata().withAnnotations(Collections.singletonMap(InitContainerHandler.INIT_CONTAINER_ANNOTATION, initContainers.toString())).endMetadata().endTemplate();
        KubernetesListBuilder klb = new KubernetesListBuilder().addToPodTemplateItems(ptb.build());

        VolumePermissionEnricher enricher = new VolumePermissionEnricher(context);
        enricher.adapt(klb);

        String initS = ((PodTemplate) klb.build().getItems().get(0)).getTemplate().getMetadata().getAnnotations().get(InitContainerHandler.INIT_CONTAINER_ANNOTATION);
        assertNotNull(initS);
        JSONArray actualInitContainers = new JSONArray(initS);
        assertEquals(1, actualInitContainers.length());
        JSONObject actualInitContainer = actualInitContainers.getJSONObject(0);
        assertEquals("blub", actualInitContainer.get("mountPath"));
    }

    @Test
    public void testAdapt() throws Exception {
        final TestConfig[] data = new TestConfig[]{
            new TestConfig(null, null),
            new TestConfig(null, VolumePermissionEnricher.ENRICHER_NAME, "volumeA"),
            new TestConfig(null, VolumePermissionEnricher.ENRICHER_NAME, "volumeA", "volumeB")
        };

        for (final TestConfig tc : data) {
            final ProcessorConfig config = new ProcessorConfig(null, null,
                    Collections.singletonMap(VolumePermissionEnricher.ENRICHER_NAME, new TreeMap(Collections
                            .singletonMap(VolumePermissionEnricher.Config.permission.name(), tc.permission))));

            // Setup mock behaviour
            new Expectations() {{ context.getConfig(); result = config; }};

            VolumePermissionEnricher enricher = new VolumePermissionEnricher(context);

            PodTemplateBuilder ptb = createEmptyPodTemplate();

            for (String vn : tc.volumeNames) {
                ptb = addVolume(ptb, vn);
            }

            KubernetesListBuilder klb = new KubernetesListBuilder().addToPodTemplateItems(ptb.build());

            enricher.adapt(klb);

            PodTemplate pt = (PodTemplate) klb.buildItem(0);

            String initContainers = pt.getTemplate().getMetadata().getAnnotations()
                    .get(InitContainerHandler.INIT_CONTAINER_ANNOTATION);
            boolean shouldHaveInitContainer = tc.volumeNames.length > 0;
            assertEquals(shouldHaveInitContainer, initContainers != null);
            if (!shouldHaveInitContainer) {
                continue;
            }

            JSONArray ja = new JSONArray(initContainers);
            assertEquals(1, ja.length());

            JSONObject jo = ja.getJSONObject(0);
            assertEquals(tc.initContainerName, jo.get("name"));
            String permission = Strings.isNullOrBlank(tc.permission) ? "777" : tc.permission;
            JSONArray chmodCmd = new JSONArray();
            chmodCmd.put("chmod");
            chmodCmd.put(permission);
            for (String vn : tc.volumeNames) {
              chmodCmd.put("/tmp/" + vn);
            }
            assertEquals(chmodCmd.toString(), jo.getJSONArray("command").toString());
        }
    }

    public PodTemplateBuilder addVolume(PodTemplateBuilder ptb, String vn) {
        ptb = ptb.editTemplate().
            editSpec().
            addNewVolume().withName(vn).withNewPersistentVolumeClaim().and().and().
            addNewVolume().withName("non-pvc").withNewEmptyDir().and().and().
            and().and();
        ptb = ptb.editTemplate().editSpec().withContainers(
            new ContainerBuilder(ptb.buildTemplate().getSpec().getContainers().get(0))
                .addNewVolumeMount().withName(vn).withMountPath("/tmp/" + vn).and()
                .addNewVolumeMount().withName("non-pvc").withMountPath("/tmp/non-pvc").and()
                .build()
           ).and().and();
        return ptb;
    }

    public PodTemplateBuilder createEmptyPodTemplate() {
        return new PodTemplateBuilder().withNewMetadata().endMetadata()
                                .withNewTemplate()
                                  .withNewMetadata().endMetadata()
                                  .withNewSpec().addNewContainer().endContainer().endSpec()
                                .endTemplate();
    }
}
