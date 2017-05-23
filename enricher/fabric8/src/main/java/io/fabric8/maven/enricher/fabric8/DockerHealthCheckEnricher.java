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
package io.fabric8.maven.enricher.fabric8;

import java.util.List;

import com.google.common.base.Objects;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ExecAction;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.maven.core.util.kubernetes.KubernetesResourceUtil;
import io.fabric8.maven.docker.config.HealthCheckConfiguration;
import io.fabric8.maven.docker.config.HealthCheckMode;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.enricher.api.AbstractHealthCheckEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;

import static io.fabric8.maven.enricher.api.util.GoTimeUtil.durationSeconds;

/**
 * Enrich a container with probes when health checks are defined in the {@code ImageConfiguration} of the docker maven plugin.
 * This enricher could need a change when Dockerfile health checks will be supported natively in Openshift or Kubernetes.
 */
public class DockerHealthCheckEnricher extends AbstractHealthCheckEnricher {

    public DockerHealthCheckEnricher(EnricherContext buildContext) {
        super(buildContext, "docker-health-check");
    }

    @Override
    protected Probe getReadinessProbe(ContainerBuilder container) {
        return getProbe(container);
    }

    @Override
    protected Probe getLivenessProbe(ContainerBuilder container) {
        return getProbe(container);
    }


    private Probe getProbe(ContainerBuilder container) {
        ImageConfiguration image = getImageWithContainerName(container.getName());
        if (image != null) {
            return getProbe(image);
        }

        return null;
    }

    private Probe getProbe(ImageConfiguration image) {
        if (hasHealthCheck(image)) {
            HealthCheckConfiguration health = image.getBuildConfiguration().getHealthCheck();
            return new ProbeBuilder()
                    .withExec(new ExecAction(health.getCmd().asStrings()))
                    .withTimeoutSeconds(durationSeconds(health.getTimeout()))
                    .withPeriodSeconds(durationSeconds(health.getInterval()))
                    .withFailureThreshold(health.getRetries())
                    .build();
        }

        return null;
    }

    private boolean hasHealthCheck(ImageConfiguration image) {
        return image.getBuildConfiguration() !=null &&
                image.getBuildConfiguration().getHealthCheck() != null &&
                image.getBuildConfiguration().getHealthCheck().getCmd() != null &&
                image.getBuildConfiguration().getHealthCheck().getMode() == HealthCheckMode.cmd;
    }

    private ImageConfiguration getImageWithContainerName(String containerName) {
        if (containerName == null) {
            return null;
        }
        List<ImageConfiguration> images = getImages();
        if (images != null) {
            for (ImageConfiguration image : images) {
                String imageContainerName = KubernetesResourceUtil.extractContainerName(getProject(), image);
                if (Objects.equal(containerName, imageContainerName)) {
                    return image;
                }
            }
        }
        return null;
    }

}
