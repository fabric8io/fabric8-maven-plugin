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
import io.fabric8.maven.plugin.mojo.build.ApplyMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.util.Set;

/**
 * Starts the application that was previously created via <code>fabric8:deploy</code> and then
 * stopped via <code>fabric8:stop</code>
 */
@Mojo(name = "start", requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.INSTALL)
public class StartMojo extends ApplyMojo {

    @Parameter(property = "fabric8.replicas", defaultValue = "1")
    private int replicas;

    @Override
    protected void applyEntities(Controller controller, KubernetesClient kubernetes, String namespace,
                                 String fileName, Set<HasMetadata> entities) throws Exception {
        resizeApp(kubernetes, namespace, entities, replicas);
    }
}


