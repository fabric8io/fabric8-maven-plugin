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
package io.fabric8.maven.plugin.mojo.develop;

import java.util.List;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.maven.plugin.mojo.build.ApplyMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import static io.fabric8.maven.core.util.kubernetes.KubernetesClientUtil.deleteEntities;

/**
 * Undeploys (deletes) the kubernetes resources generated by the current project.
 * <br>
 * This goal is the opposite to the <code>fabric8:run</code> or <code>fabric8:deploy</code> goals.
 */
@Mojo(name = "undeploy", requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.INSTALL)
public class UndeployMojo extends ApplyMojo {
    @Override
    protected void applyEntities(KubernetesClient kubernetes, String namespace, String fileName, Set<HasMetadata> entities) throws Exception {
        deleteCustomEntities(kubernetes, namespace, resources != null ? resources.getCrdContexts() : null);
        deleteEntities(kubernetes, namespace, entities, s2iBuildNameSuffix, log);
    }

    private void deleteCustomEntities(KubernetesClient kubernetes, String namespace, List<String> customResourceDefinitions) throws Exception {
        processCustomEntities(kubernetes, namespace, customResourceDefinitions, true);
    }
}
