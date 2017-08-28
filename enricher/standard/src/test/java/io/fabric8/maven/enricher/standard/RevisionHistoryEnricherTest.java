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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jayway.jsonpath.matchers.JsonPathMatchers;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.KubernetesResourceUtil;
import io.fabric8.maven.enricher.api.EnricherContext;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@RunWith(JMockit.class)
public class RevisionHistoryEnricherTest {

    @Mocked
    private EnricherContext context;

    @Test
    public void testDefaultRevisionHistoryLimit() throws JsonProcessingException {
        // Given
        KubernetesListBuilder builder = new KubernetesListBuilder()
                .addNewDeploymentItem()
                .endDeploymentItem();

        RevisionHistoryEnricher enricher = new RevisionHistoryEnricher(context);

        // When
        enricher.addMissingResources(builder);

        // Then
        assertRevisionHistory(builder.build(), Configs.asInt(RevisionHistoryEnricher.Config.limit.def()));
    }

    @Test
    public void testCustomRevisionHistoryLimit() throws JsonProcessingException {

        // Setup mock behaviour
        final String revisionNumber = "10";
        new Expectations() {{
            context.getConfig(); result = prepareEnricherConfig(revisionNumber);
        }};

        // Given
        KubernetesListBuilder builder = new KubernetesListBuilder()
                .addNewDeploymentItem()
                .endDeploymentItem();

        RevisionHistoryEnricher enricher = new RevisionHistoryEnricher(context);

        // When
        enricher.addMissingResources(builder);

        // Then
        assertRevisionHistory(builder.build(), Integer.parseInt(revisionNumber));
    }

    private ProcessorConfig prepareEnricherConfig(final String revisionNumber) {
        return new ProcessorConfig(
                    null,
                    null,
                    Collections.singletonMap(
                            RevisionHistoryEnricher.DEFAULT_NAME,
                            new TreeMap(Collections.singletonMap(
                                    RevisionHistoryEnricher.Config.limit.name(),
                                    revisionNumber)
                            )
                    )
                );
    }

    private void assertRevisionHistory(KubernetesList list, Integer revisionNumber) throws JsonProcessingException {
        assertEquals(list.getItems().size(),1);

        String kubeJson = KubernetesResourceUtil.toJson(list.getItems().get(0));
        assertThat(kubeJson, JsonPathMatchers.isJson());
        assertThat(kubeJson, JsonPathMatchers.hasJsonPath("$.spec.revisionHistoryLimit", Matchers.equalTo(revisionNumber)));
    }

}