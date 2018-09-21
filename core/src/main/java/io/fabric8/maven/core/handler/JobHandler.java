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

import java.util.List;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.api.model.batch.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.JobSpec;
import io.fabric8.kubernetes.api.model.batch.JobSpecBuilder;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.util.kubernetes.KubernetesHelper;
import io.fabric8.maven.docker.config.ImageConfiguration;

/**
 * Handler for Jobs
 *
 * @author matthew on 11/02/17.
 */
public class JobHandler {
    private final PodTemplateHandler podTemplateHandler;

    JobHandler(PodTemplateHandler podTemplateHandler) {
        this.podTemplateHandler = podTemplateHandler;
    }

    public Job getJob(ResourceConfig config,
                      List<ImageConfiguration> images) {
        return new JobBuilder()
                .withMetadata(createJobSpecMetaData(config))
                .withSpec(createJobSpec(config, images))
                .build();
    }

    // ===========================================================

    private ObjectMeta createJobSpecMetaData(ResourceConfig config) {
        return new ObjectMetaBuilder()
                .withName(KubernetesHelper.validateKubernetesId(config.getControllerName(), "controller name"))
                .build();
    }

    private JobSpec createJobSpec(ResourceConfig config, List<ImageConfiguration> images) {
        return new JobSpecBuilder()
                .withTemplate(podTemplateHandler.getPodTemplate(config, images))
                .build();
    }
}
