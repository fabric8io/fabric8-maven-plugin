package io.fabric8.maven.enricher.standard;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.extensions.DaemonSet;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.PetSet;
import io.fabric8.maven.core.config.ProbeConfig;
import io.fabric8.maven.core.handler.ProbeHandler;
import io.fabric8.maven.enricher.api.AbstractHealthCheckEnricher;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;

import java.util.Collections;
import java.util.List;

/**
 * Created by matthew.costa on 26/10/2016.
 */
public class HealthCheckEnricher extends BaseEnricher {

    private ProbeConfig liveness;
    private ProbeConfig readiness;
    private ProbeHandler probeHandler;

    public HealthCheckEnricher(EnricherContext buildContext) {
        super(buildContext, "fmp-healthcheck");

        probeHandler = new ProbeHandler();
        liveness = buildContext.getResourceConfig().getLiveness();
        readiness = buildContext.getResourceConfig().getReadiness();
    }

    @Override
    public void addMissingResources(KubernetesListBuilder builder) {
        List<HasMetadata> items = builder.getItems();
        for (HasMetadata item : items) {
            List<Container> containers = null;

            if(item instanceof Deployment) {
                Deployment deployment = (Deployment) item;
                containers = deployment.getSpec().getTemplate().getSpec().getContainers();
                log.info("Enriching Deployment with health checks");
            } else if(item instanceof PetSet) {
                PetSet petSet = (PetSet) item;
                containers = petSet.getSpec().getTemplate().getSpec().getContainers();
                log.info("Enriching PetSet with health checks");
            } else if(item instanceof DaemonSet) {
                DaemonSet daemonSet = (DaemonSet) item;
                containers = daemonSet.getSpec().getTemplate().getSpec().getContainers();
                log.info("Enriching DaemonSet with health checks");
            } else {
                continue;
            }

            assert containers != null;
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
        builder.withItems(items);
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

    protected Probe getReadinessProbe() {
        return probeHandler.getProbe(readiness);
    }

    protected Probe getLivenessProbe() {
        return probeHandler.getProbe(liveness);
    }
}
