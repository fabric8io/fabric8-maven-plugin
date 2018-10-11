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
package io.fabric8.maven.enricher.standard;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import io.fabric8.openshift.api.model.ImageChangeTrigger;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.junit.Test;
import org.junit.runner.RunWith;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

/**
 * @author nicola
 */
@RunWith(JMockit.class)
public class TriggersAnnotationEnricherTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mocked
    private MavenEnricherContext context;

    @Test
    public void testStatefulSetEnrichment() throws IOException {

        KubernetesListBuilder builder = new KubernetesListBuilder()
                .addNewStatefulSetItem()
                    .withNewSpec()
                        .withNewTemplate()
                            .withNewSpec()
                                .withContainers(createContainers("c1", "is:latest"))
                            .endSpec()
                        .endTemplate()
                    .endSpec()
                .endStatefulSetItem();


        TriggersAnnotationEnricher enricher = new TriggersAnnotationEnricher(context);
        enricher.adapt(builder);


        StatefulSet res = (StatefulSet) builder.build().getItems().get(0);
        String triggers = res.getMetadata().getAnnotations().get("image.openshift.io/triggers");
        assertNotNull(triggers);

        List<ImageChangeTrigger> triggerList = OBJECT_MAPPER.readValue(triggers, OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, ImageChangeTrigger.class));
        assertEquals(1, triggerList.size());

        ImageChangeTrigger trigger = triggerList.get(0);
        assertEquals("ImageStreamTag", trigger.getFrom().getKind());
        assertEquals("is:latest", trigger.getFrom().getName());
        assertTrue(trigger.getAdditionalProperties().containsKey("fieldPath"));

    }

    @Test
    public void testReplicaSetEnrichment() throws IOException {

        KubernetesListBuilder builder = new KubernetesListBuilder()
                .addNewReplicaSetItem()
                    .withNewSpec()
                        .withNewTemplate()
                            .withNewSpec()
                                .withContainers(createContainers(
                                    "c1", "is",
                                    "c2", "a-docker-user/is:latest"
                                ))
                            .endSpec()
                        .endTemplate()
                    .endSpec()
                .endReplicaSetItem();


        TriggersAnnotationEnricher enricher = new TriggersAnnotationEnricher(context);
        enricher.adapt(builder);


        ReplicaSet res = (ReplicaSet) builder.build().getItems().get(0);
        String triggers = res.getMetadata().getAnnotations().get("image.openshift.io/triggers");
        assertNotNull(triggers);

        List<ImageChangeTrigger> triggerList = OBJECT_MAPPER.readValue(triggers, OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, ImageChangeTrigger.class));
        assertEquals(1, triggerList.size());

        ImageChangeTrigger trigger = triggerList.get(0);
        assertEquals("ImageStreamTag", trigger.getFrom().getKind());
        assertEquals("is:latest", trigger.getFrom().getName());
        assertTrue(trigger.getAdditionalProperties().containsKey("fieldPath"));
    }

    @Test
    public void testDaemonSetEnrichment() throws IOException {

        KubernetesListBuilder builder = new KubernetesListBuilder()
                .addNewDaemonSetItem()
                    .withNewMetadata()
                        .addToAnnotations("annkey", "annvalue")
                    .endMetadata()
                    .withNewSpec()
                        .withNewTemplate()
                            .withNewSpec()
                                .withContainers(createContainers(
                                    "c1", "iss:1.1.0",
                                    "c2", "docker.io/a-docker-user/is:latest"
                                ))
                            .endSpec()
                        .endTemplate()
                    .endSpec()
                .endDaemonSetItem();


        TriggersAnnotationEnricher enricher = new TriggersAnnotationEnricher(context);
        enricher.adapt(builder);


        DaemonSet res = (DaemonSet) builder.build().getItems().get(0);
        String triggers = res.getMetadata().getAnnotations().get("image.openshift.io/triggers");
        assertNotNull(triggers);

        List<ImageChangeTrigger> triggerList = OBJECT_MAPPER.readValue(triggers, OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, ImageChangeTrigger.class));
        assertEquals(1, triggerList.size());

        ImageChangeTrigger trigger = triggerList.get(0);
        assertEquals("ImageStreamTag", trigger.getFrom().getKind());
        assertEquals("iss:1.1.0", trigger.getFrom().getName());
        assertTrue(trigger.getAdditionalProperties().containsKey("fieldPath"));

        assertEquals("annvalue", res.getMetadata().getAnnotations().get("annkey"));
    }

    @Test
    public void testConditionalStatefulSetEnrichment() throws IOException {

        final Properties props = new Properties();
        props.put("fabric8.enricher.fmp-triggers-annotation.containers", "c2, c3, anotherc");
        new Expectations() {{
            context.getProperties();
            result = props;
        }};

        KubernetesListBuilder builder = new KubernetesListBuilder()
                .addNewStatefulSetItem()
                    .withNewSpec()
                        .withNewTemplate()
                            .withNewSpec()
                                .withContainers(createContainers(
                                    "c1", "is1:latest",
                                    "c2", "is2:latest",
                                    "c3", "is3:latest"
                                ))
                            .endSpec()
                        .endTemplate()
                    .endSpec()
                .endStatefulSetItem();


        TriggersAnnotationEnricher enricher = new TriggersAnnotationEnricher(context);
        enricher.adapt(builder);


        StatefulSet res = (StatefulSet) builder.build().getItems().get(0);
        String triggers = res.getMetadata().getAnnotations().get("image.openshift.io/triggers");
        assertNotNull(triggers);

        List<ImageChangeTrigger> triggerList = OBJECT_MAPPER.readValue(triggers, OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, ImageChangeTrigger.class));
        assertEquals(2, triggerList.size());

        ImageChangeTrigger trigger1 = triggerList.get(0);
        assertEquals("ImageStreamTag", trigger1.getFrom().getKind());
        assertEquals("is2:latest", trigger1.getFrom().getName());
        assertTrue(trigger1.getAdditionalProperties().containsKey("fieldPath"));

        ImageChangeTrigger trigger2 = triggerList.get(1);
        assertEquals("ImageStreamTag", trigger2.getFrom().getKind());
        assertEquals("is3:latest", trigger2.getFrom().getName());
        assertTrue(trigger2.getAdditionalProperties().containsKey("fieldPath"));
    }

    @Test
    public void testNoEnrichment() {

        KubernetesListBuilder builder = new KubernetesListBuilder()
                .addNewJobItem()
                    .withNewMetadata()
                        .addToAnnotations("dummy", "annotation")
                    .endMetadata()
                    .withNewSpec()
                        .withNewTemplate()
                            .withNewSpec()
                                .withContainers(createContainers(
                                    "c1", "is1:latest",
                                    "c2", "is2:latest"
                                ))
                            .endSpec()
                        .endTemplate()
                    .endSpec()
                .endJobItem();


        TriggersAnnotationEnricher enricher = new TriggersAnnotationEnricher(context);
        enricher.adapt(builder);


        Job res = (Job) builder.build().getItems().get(0);
        String triggers = res.getMetadata().getAnnotations().get("image.openshift.io/triggers");
        assertNull(triggers);
    }


    private List<Container> createContainers(String... nameImage) {
        assertEquals(0, nameImage.length % 2);
        List<Container> containers = new ArrayList<>();
        for (int i=0; i<nameImage.length; i+=2) {
            Container container = new ContainerBuilder()
                    .withName(nameImage[i])
                    .withImage(nameImage[i+1])
                    .build();
            containers.add(container);
        }

        return containers;
    }

}
