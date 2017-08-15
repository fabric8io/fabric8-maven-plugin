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

package io.fabric8.maven.core.util;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentBuilder;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;

/**
 */
public class MergeResourceTest {
    private Logger log = new AnsiLogger(new SystemStreamLog(), false, false);

    @Test
    public void testMergeDeploymentMetadataAndEnvVars() throws Exception {
        Deployment resource = new DeploymentBuilder().withNewMetadata().withName("cheese").
                addToAnnotations("overwriteKey", "originalValue").
                addToAnnotations("unchangedKey", "shouldNotChange").
                addToAnnotations("unchangedBlankKey", "").
                addToAnnotations("deletedKey", "shouldBeDeleted").
                endMetadata().
                withNewSpec().withNewTemplate().withNewSpec().addNewContainer().
                    withImage("cheese-image").
                addToEnv(new EnvVarBuilder().withName("ENV_UPDATED").withValue("OLD_VALUE").build()).
                addToEnv(new EnvVarBuilder().withName("ENV_UNMODIFIED").withValue("SHOULD_NOT_CHANGE").build()).
                addToEnv(new EnvVarBuilder().withName("ENV_DELETED").withValue("DELETE_ME").build()).
                endContainer().endSpec().endTemplate().endSpec().
                build();


        Deployment override = new DeploymentBuilder().withNewMetadata().withName("cheese").
                addToAnnotations("overwriteKey", "newValue").
                addToAnnotations("deletedKey", "").endMetadata().
                withNewSpec().withNewTemplate().withNewSpec().addNewContainer().
                    addToEnv(new EnvVarBuilder().withName("ENV_UPDATED").withValue("NEW_VALUE").build()).
                    addToEnv(new EnvVarBuilder().withName("ENV_DELETED").withValue("").build()).
                    addToEnv(new EnvVarBuilder().withName("ENV_ADDED").withValue("ADDED_VALUE").build()).
                endContainer().endSpec().endTemplate().endSpec().
                build();


        HasMetadata answer = KubernetesResourceUtil.mergeResources(resource, override, log, false);
        assertThat(answer).describedAs("mergeResult").isInstanceOf(Deployment.class);
        Deployment result = (Deployment) answer;

        log.info("Override metadata on Deployment generated: " + KubernetesHelper.toYaml(answer));
        Map<String, String> annotations = answer.getMetadata().getAnnotations();

        assertDataModified(annotations, "Deployment.metadata.annotations");
        assertDataNotModified(resource.getMetadata().getAnnotations(), "Original Deployment.metadata.annotations");

        assertEnvModified(result.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv(), "Deployment.spec.template.spec.containers[0].env");
        assertEnvNotModified(resource.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv(), "Original Deployment.spec.template.spec.containers[0].env");
    }


    @Test
    public void testMergeDeploymentMetadataWithNoSpec() throws Exception {
        Deployment resource = new DeploymentBuilder().withNewMetadata().withName("cheese").
                addToAnnotations("overwriteKey", "originalValue").
                addToAnnotations("unchangedKey", "shouldNotChange").
                addToAnnotations("unchangedBlankKey", "").
                addToAnnotations("deletedKey", "shouldBeDeleted").
                endMetadata().
                withNewSpec().withNewTemplate().withNewSpec().addNewContainer().withImage("cheese-image").endContainer().endSpec().endTemplate().endSpec().
                build();


        Deployment override = new DeploymentBuilder().withNewMetadata().withName("cheese").
                addToAnnotations("overwriteKey", "newValue").
                addToAnnotations("deletedKey", "").
                endMetadata().
                build();


        HasMetadata answer = KubernetesResourceUtil.mergeResources(resource, override, log, false);
        assertNotNull(answer);

        log.info("Override metadata on Deployment with no spec generated: " + KubernetesHelper.toYaml(answer));
        Map<String, String> annotations = answer.getMetadata().getAnnotations();

        assertDataModified(annotations, "Deployment.metadata.annotations");
        assertDataNotModified(resource.getMetadata().getAnnotations(), "Original Deployment.metadata.annotations");
    }

    @Test
    public void testMergeDeploymentTemplateMetadata() throws Exception {
        Deployment resource = new DeploymentBuilder().withNewMetadata().withName("cheese").endMetadata().
                withNewSpec().withNewTemplate().
                withNewSpec().addNewContainer().withImage("cheese-image").endContainer().endSpec().
                withNewMetadata().
                addToAnnotations("overwriteKey", "originalValue").
                addToAnnotations("unchangedKey", "shouldNotChange").
                addToAnnotations("unchangedBlankKey", "").
                addToAnnotations("deletedKey", "shouldBeDeleted").
                endMetadata().
                endTemplate().endSpec().
                build();


        Deployment override = new DeploymentBuilder().withNewMetadata().withName("cheese").endMetadata().
                withNewSpec().withNewTemplate().
                withNewSpec().addNewContainer().addToEnv(new EnvVarBuilder().withName("ENV_FOO").withValue("FOO_VALUE").build()).endContainer().endSpec().
                withNewMetadata().
                addToAnnotations("overwriteKey", "newValue").
                addToAnnotations("deletedKey", "").
                endMetadata().
                endTemplate().endSpec().
                build();


        HasMetadata answer = KubernetesResourceUtil.mergeResources(resource, override, log, false);
        assertNotNull(answer);

        log.info("Override metadata on Deployment generated: " + KubernetesHelper.toYaml(answer));

        assertThat(answer).describedAs("mergeResult").isInstanceOf(Deployment.class);


        Deployment deployment = (Deployment) answer;
        Map<String, String> annotations = deployment.getSpec().getTemplate().getMetadata().getAnnotations();

        assertDataModified(annotations, "Deployment.spec.template.metadata.annotations");
        assertDataNotModified(resource.getSpec().getTemplate().getMetadata().getAnnotations(), "Original Deployment.spec.template.metadata.annotations");
    }

    @Test
    public void testMergeConfigMap() throws Exception {
        ConfigMap resource = new ConfigMapBuilder().withNewMetadata().withName("cheese").endMetadata().
                addToData("overwriteKey", "originalValue").
                addToData("unchangedKey", "shouldNotChange").
                addToData("unchangedBlankKey", "").
                addToData("deletedKey", "shouldBeDeleted").
                build();


        ConfigMap override = new ConfigMapBuilder().withNewMetadata().withName("cheese").endMetadata().
                addToData("overwriteKey", "newValue").
                addToData("deletedKey", "").
                build();


        HasMetadata answer = KubernetesResourceUtil.mergeResources(resource, override, log, false);

        log.info("Override ConfigMap generated: " + KubernetesHelper.toYaml(answer));

        assertThat(answer).describedAs("mergeResult").isInstanceOf(ConfigMap.class);

        ConfigMap cm = (ConfigMap) answer;
        Map<String, String> data = cm.getData();
        
        assertDataModified(data, "ConfigMap.data");
        assertDataNotModified(resource.getData(), "Original ConfigMap.data");
    }

    protected void assertDataModified(Map<String, String> annotations, String description) {
        assertThat(annotations).describedAs(description).
                containsEntry("overwriteKey", "newValue").
                containsEntry("unchangedKey", "shouldNotChange").
                containsEntry("unchangedBlankKey", "").
                doesNotContainKeys("deletedKey");
    }

    protected void assertDataNotModified(Map<String, String> annotations, String description) {
        assertThat(annotations).describedAs(description).
                containsEntry("overwriteKey", "originalValue").
                containsEntry("unchangedKey", "shouldNotChange").
                containsEntry("unchangedBlankKey", "").
                containsEntry("deletedKey", "shouldBeDeleted");
    }


    protected void assertEnvModified(List<EnvVar> env, String description) {
        Map<String, String> envMap = envVarToMap(env);
        assertThat(envMap).describedAs(description).
                containsEntry("ENV_UPDATED", "NEW_VALUE").
                containsEntry("ENV_UNMODIFIED", "SHOULD_NOT_CHANGE").
                containsEntry("ENV_ADDED", "ADDED_VALUE").
                doesNotContainKeys("ENV_DELETED");
    }

    protected void assertEnvNotModified(List<EnvVar> env, String description) {
        Map<String, String> envMap = envVarToMap(env);
        assertThat(envMap).describedAs(description).
                containsEntry("ENV_UPDATED", "OLD_VALUE").
                containsEntry("ENV_UNMODIFIED", "SHOULD_NOT_CHANGE").
                containsEntry("ENV_DELETED", "DELETE_ME").
                doesNotContainKeys("ENV_ADDED");
    }

    private Map<String, String> envVarToMap(List<EnvVar> envVars) {
        Map<String, String> answer = new HashMap<>();
        if (envVars != null) {
            for (EnvVar envVar : envVars) {
                String value = envVar.getValue();
                if (value != null && !value.isEmpty()) {
                    String name = envVar.getName();
                    answer.put(name, value);
                }
            }
        }
        return answer;
    }

}

