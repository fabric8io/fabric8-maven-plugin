package io.fabric8.maven.enricher.api;

import java.util.Collections;
import java.util.List;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.extensions.Deployment;

/**
 * Enriches containers with health check probes.
 */
public abstract class AbstractHealthCheckEnricher extends BaseEnricher {

    public AbstractHealthCheckEnricher(EnricherContext buildContext, String name) {
        super(buildContext, name);
    }

    @Override
    public void addMissingResources(KubernetesListBuilder builder) {

        List<HasMetadata> items = builder.getItems();
        for (HasMetadata item : items) {
            if (item instanceof Deployment) {
                Deployment deployment = (Deployment) item;
                List<Container> containers = getCandidateContainers(deployment);
                for (Container container : containers) {
                    if (container.getReadinessProbe() == null) {
                        Probe probe = getReadinessProbe();
                        if (probe != null) {
                            log.info("Adding readiness " + describe(probe));
                            container.setReadinessProbe(probe);
                        }
                    }

                    if (container.getLivenessProbe() == null) {
                        Probe probe = getLivenessProbe();
                        if (probe != null) {
                            log.info("Adding liveness " + describe(probe));
                            container.setLivenessProbe(probe);
                        }
                    }
                }
            }
        }
        builder.withItems(items);
    }

    private List<Container> getCandidateContainers(Deployment deployment) {
        if (deployment.getSpec() != null &&
                deployment.getSpec().getTemplate() != null &&
                deployment.getSpec().getTemplate().getSpec() != null &&
                deployment.getSpec().getTemplate().getSpec().getContainers() != null &&
                deployment.getSpec().getTemplate().getSpec().getContainers().size() > 0) {

            List<Container> containers = deployment.getSpec().getTemplate().getSpec().getContainers();
            if (containers != null && containers.size() > 0) {
                return Collections.singletonList(containers.get(containers.size() - 1)); // enhance last container only
            }
        }

        return Collections.emptyList();
    }

    private String describe(Probe probe) {
        StringBuilder desc = new StringBuilder("probe");
        if (probe.getHttpGet() != null) {
            desc.append(" on port ");
            desc.append(probe.getHttpGet().getPort().getIntVal());
            desc.append(", path='");
            desc.append(probe.getHttpGet().getPath());
            desc.append("'");
        }
        if (probe.getInitialDelaySeconds() != null) {
            desc.append(", with initial delay ");
            desc.append(probe.getInitialDelaySeconds());
            desc.append(" seconds");
        }
        if (probe.getPeriodSeconds() != null) {
            desc.append(", with period ");
            desc.append(probe.getPeriodSeconds());
            desc.append(" seconds");
        }
        return desc.toString();
    }

    protected abstract Probe getReadinessProbe();

    protected abstract Probe getLivenessProbe();

}
