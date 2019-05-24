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
package io.fabric8.maven.core.handler;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.util.kubernetes.KubernetesHelper;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.api.model.ProjectBuilder;

public class ProjectHandler {
    public Project getProject(String namespace) {
        return new ProjectBuilder().withMetadata(createProjectMetaData(namespace)).withNewStatus("active").build();
    }

    private ObjectMeta createProjectMetaData(String namespace) {
        return new ObjectMetaBuilder()
                .withName(KubernetesHelper.validateKubernetesId(namespace, "project name"))
                .build();
    }
}
