package io.fabric8.maven.enricher.fabric8;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ExecAction;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.maven.core.util.KubernetesResourceUtil;
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

    /**
     * Try to provide a health check for any image defined in the config.
     */
    @Override
    protected List<Container> getCandidateContainers(Deployment deployment) {
        if (deployment.getSpec() != null &&
                deployment.getSpec().getTemplate() != null &&
                deployment.getSpec().getTemplate().getSpec() != null) {

            List<Container> containers = deployment.getSpec().getTemplate().getSpec().getContainers();
            if (containers != null) {
                return containers;
            }
        }

        return Collections.emptyList();
    }

    @Override
    protected Probe getReadinessProbe(Container container) {
        return getProbe(container);
    }

    @Override
    protected Probe getLivenessProbe(Container container) {
        return getProbe(container);
    }

    protected Probe getProbe(ImageConfiguration image) {
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

    protected Probe getProbe(Container container) {
        String imageName = container.getImage();
        if (imageName == null) {
            return null;
        }

        ImageConfiguration containerImage = null;
        List<ImageConfiguration> images = getImages();
        if (images != null) {
            List<ImageConfiguration> candidate = new LinkedList<>();
            for (ImageConfiguration image : images) {
                if (imageName.equals(image.getName())) {
                    candidate.add(image);
                }
            }

            if (candidate.size() == 1) {
                // Just one match, choosing it
                containerImage = candidate.get(0);
            } else if (candidate.size() > 1) {
                // More than one match, matching with alias
                for (ImageConfiguration image : images) {
                    String cName = KubernetesResourceUtil.extractContainerName(getProject(), image);
                    if (cName != null && cName.equals(container.getName())) {
                        containerImage = image;
                        break;
                    }
                }

                if (containerImage == null) {
                    // Or getting the first matching image with health checks
                    for (ImageConfiguration image : images) {
                        if (hasHealthCheck(image)) {
                            containerImage = image;
                            break;
                        }
                    }
                }
            }
        }

        return getProbe(containerImage);
    }

    private boolean hasHealthCheck(ImageConfiguration image) {
        return image != null &&
                image.getBuildConfiguration() !=null &&
                image.getBuildConfiguration().getHealthCheck() != null &&
                image.getBuildConfiguration().getHealthCheck().getMode() == HealthCheckMode.cmd;
    }

}
