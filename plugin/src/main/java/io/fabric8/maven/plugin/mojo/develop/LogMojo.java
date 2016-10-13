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

package io.fabric8.maven.plugin.mojo.develop;

import io.fabric8.kubernetes.api.Controller;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.util.Set;

/**
 * This goal tails the log of the most recent pod for the app that was deployed via <code>fabric8:deploy</code>
 * <p>
 * To terminate the log hit
 * <code>Ctrl+C</code>
 */
@Mojo(name = "log", requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.VALIDATE)
public class LogMojo extends AbstractTailLogMojo {

    @Parameter(property = "fabric8.log.follow", defaultValue = "true")
    private boolean followLog;

    @Override
    protected void applyEntities(Controller controller, final KubernetesClient kubernetes, final String namespace, String fileName, final Set<HasMetadata> entities) throws Exception {
        tailAppPodsLogs(kubernetes, namespace, entities, false, null, followLog, null, true);
    }


}
