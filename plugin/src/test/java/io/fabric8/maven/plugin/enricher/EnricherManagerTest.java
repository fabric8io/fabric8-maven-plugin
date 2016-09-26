package io.fabric8.maven.plugin.enricher;
/*
 *
 * Copyright 2016 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.*;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.enricher.api.EnricherContext;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * @author roland
 * @since 23/09/16
 */
@RunWith(JMockit.class)
public class EnricherManagerTest {

    @Mocked
    private EnricherContext context;

    @Test
    public void createDefaultResources() {
        new Expectations() {{
           context.getConfig(); result = ProcessorConfig.EMPTY;
        }};
        EnricherManager manager = new EnricherManager(context);

        KubernetesListBuilder builder = new KubernetesListBuilder();
        manager.createDefaultResources(builder);
        assertTrue(builder.build().getItems().size() > 0);
    }

    @Test
    public void enrichEmpty() {
        new Expectations() {{
           context.getConfig(); result = ProcessorConfig.EMPTY;
        }};
        EnricherManager manager = new EnricherManager(context);

        KubernetesListBuilder builder = new KubernetesListBuilder();
        manager.enrich(builder);
        assertEquals(0,builder.build().getItems().size(),1);
    }

    @Test
    public void enrichSimple() {
        new Expectations() {{
           context.getConfig(); result = new ProcessorConfig(Arrays.asList("fmp-project"),null,new HashMap());
        }};
        EnricherManager manager = new EnricherManager(context);

        KubernetesListBuilder builder = new KubernetesListBuilder();
        builder.addNewReplicaSetItem()
               .withNewSpec()
                 .withNewTemplate()
                   .withNewSpec()
                     .addNewContainer()
                       .withName("test")
                       .withImage("busybox")
                     .endContainer()
                   .endSpec()
                 .endTemplate()
               .endSpec()
               .endReplicaSetItem();
        manager.enrich(builder);
        KubernetesList list = builder.build();
        assertEquals(1, list.getItems().size());
        ReplicaSet pod = (ReplicaSet) list.getItems().get(0);
        Map labels = pod.getMetadata().getLabels();
        assertNotNull(labels);
        assertEquals("fabric8", labels.get("provider"));
    }
}
