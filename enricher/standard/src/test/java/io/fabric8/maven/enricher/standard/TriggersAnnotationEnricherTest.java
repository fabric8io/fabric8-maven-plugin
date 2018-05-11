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

import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.extensions.DaemonSet;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.fabric8.maven.core.util.JSONUtil;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.openshift.api.model.ImageChangeTrigger;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;

import static junit.framework.TestCase.*;

/**
 * @author kameshs
 */
@RunWith(JMockit.class)
public class TriggersAnnotationEnricherTest {

    @Mocked
    private EnricherContext context;

    @Test
    public void testStatefulSetEnrichment() throws IOException {

        KubernetesListBuilder builder = new KubernetesListBuilder()
                .addNewStatefulSetItem()
                    .withNewSpec()
                        .withNewTemplate()
                            .withNewSpec()
                                .addNewContainer()
                                    .withName("c1")
                                    .withImage("is:latest")
                                .endContainer()
                            .endSpec()
                        .endTemplate()
                    .endSpec()
                .endStatefulSetItem();


        TriggersAnnotationEnricher enricher = new TriggersAnnotationEnricher(context);
        enricher.adapt(builder);


        StatefulSet res = (StatefulSet) builder.build().getItems().get(0);
        String triggers = res.getMetadata().getAnnotations().get("image.openshift.io/triggers");
        assertNotNull(triggers);

        List<ImageChangeTrigger> triggerList = JSONUtil.mapper().readValue(triggers, JSONUtil.mapper().getTypeFactory().constructCollectionType(List.class, ImageChangeTrigger.class));
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
                                .addNewContainer()
                                    .withName("c1")
                                    .withImage("is")
                                .endContainer()
                                .addNewContainer()
                                    .withName("c2")
                                    .withImage("a-docker-user/is:latest")
                                .endContainer()
                            .endSpec()
                        .endTemplate()
                    .endSpec()
                .endReplicaSetItem();


        TriggersAnnotationEnricher enricher = new TriggersAnnotationEnricher(context);
        enricher.adapt(builder);


        ReplicaSet res = (ReplicaSet) builder.build().getItems().get(0);
        String triggers = res.getMetadata().getAnnotations().get("image.openshift.io/triggers");
        assertNotNull(triggers);

        List<ImageChangeTrigger> triggerList = JSONUtil.mapper().readValue(triggers, JSONUtil.mapper().getTypeFactory().constructCollectionType(List.class, ImageChangeTrigger.class));
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
                    .withNewSpec()
                        .withNewTemplate()
                            .withNewSpec()
                                .addNewContainer()
                                    .withName("c1")
                                    .withImage("iss:1.1.0")
                                .endContainer()
                                .addNewContainer()
                                    .withName("c2")
                                    .withImage("docker.io/a-docker-user/is:latest")
                                .endContainer()
                            .endSpec()
                        .endTemplate()
                    .endSpec()
                .endDaemonSetItem();


        TriggersAnnotationEnricher enricher = new TriggersAnnotationEnricher(context);
        enricher.adapt(builder);


        DaemonSet res = (DaemonSet) builder.build().getItems().get(0);
        String triggers = res.getMetadata().getAnnotations().get("image.openshift.io/triggers");
        assertNotNull(triggers);

        List<ImageChangeTrigger> triggerList = JSONUtil.mapper().readValue(triggers, JSONUtil.mapper().getTypeFactory().constructCollectionType(List.class, ImageChangeTrigger.class));
        assertEquals(1, triggerList.size());

        ImageChangeTrigger trigger = triggerList.get(0);
        assertEquals("ImageStreamTag", trigger.getFrom().getKind());
        assertEquals("iss:1.1.0", trigger.getFrom().getName());
        assertTrue(trigger.getAdditionalProperties().containsKey("fieldPath"));
    }

}
