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
package io.fabric8.maven.plugin.enricher;

import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.model.Configuration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import io.fabric8.maven.enricher.api.util.ProjectClassLoaders;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import mockit.Expectations;
import mockit.Mocked;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author roland
 * @since 23/09/16
 */
public class EnricherManagerTest {

    @Mocked
    private MavenEnricherContext context;

    @Test
    public void createDefaultResources() {
        new Expectations() {{
            context.getConfiguration();
            result = new Configuration.Builder()
                .processorConfig(new ProcessorConfig(Arrays.asList("fmp-controller"), null, null))
                .images(Arrays.asList(new ImageConfiguration.Builder().alias("img1").name("img1").build()))
                .build();
        }};
        EnricherManager manager = new EnricherManager(null, context, Optional.empty());

        KubernetesListBuilder builder = new KubernetesListBuilder();
        manager.createDefaultResources(PlatformMode.kubernetes, builder);
        assertTrue(builder.build().getItems().size() > 0);
    }

    @Test
    public void enrichEmpty() {
        new Expectations() {{
            context.getConfiguration();
            result = new Configuration.Builder()
                .processorConfig(ProcessorConfig.EMPTY)
                .build();
        }};
        EnricherManager manager = new EnricherManager(null, context, Optional.empty());

        KubernetesListBuilder builder = new KubernetesListBuilder();
        manager.enrich(PlatformMode.kubernetes, builder);
        assertEquals(0,builder.build().getItems().size(),1);
    }

    @Test
    public void enrichSimple() {
        new Expectations() {{
            context.getConfiguration();
            result = new Configuration.Builder()
                .processorConfig(new ProcessorConfig(Arrays.asList("fmp-metadata", "fmp-project"),null,new HashMap<>()))
                .build();
        }};
        EnricherManager manager = new EnricherManager(null, context, Optional.empty());

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
        manager.enrich(PlatformMode.kubernetes, builder);
        KubernetesList list = builder.build();
        assertEquals(1, list.getItems().size());
        ReplicaSet pod = (ReplicaSet) list.getItems().get(0);
        ObjectMeta metadata = pod.getMetadata();
        assertNotNull(metadata);
        Map<String, String> labels = metadata.getLabels();
        assertNotNull(labels);
        assertEquals("fabric8", labels.get("provider"));
    }

}
