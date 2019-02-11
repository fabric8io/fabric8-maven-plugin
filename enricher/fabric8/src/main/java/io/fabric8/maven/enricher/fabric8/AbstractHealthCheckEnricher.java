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

package io.fabric8.maven.enricher.fabric8;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;

/**
 * Enriches containers with health check probes.
 */
public abstract class AbstractHealthCheckEnricher extends BaseEnricher {

    public AbstractHealthCheckEnricher(MavenEnricherContext buildContext, String name) {
        super(buildContext, name);
    }

    @Override
    public void addMissingResources(PlatformMode platformMode, KubernetesListBuilder builder) {

        builder.accept(new TypedVisitor<ContainerBuilder>() {
            @Override
            public void visit(ContainerBuilder container) {
                if (!container.hasReadinessProbe()) {
                    Probe probe = getReadinessProbe(container);
                    if (probe != null) {
                        log.info("Adding readiness " + describe(probe));
                        container.withReadinessProbe(probe);
                    }
                }

                if (!container.hasLivenessProbe()) {
                    Probe probe = getLivenessProbe(container);
                    if (probe != null) {
                        log.info("Adding liveness " + describe(probe));
                        container.withLivenessProbe(probe);
                    }
                }
            }
        });
    }

    private String describe(Probe probe) {
        StringBuilder desc = new StringBuilder("probe");
        if (probe.getHttpGet() != null) {
            desc.append(" on port ");
            desc.append(probe.getHttpGet().getPort().getIntVal());
            desc.append(", path='");
            desc.append(probe.getHttpGet().getPath());
            desc.append("'");
            desc.append(", scheme='");
            desc.append(probe.getHttpGet().getScheme());
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

    /**
     * Override this method to create a per-container readiness probe.
     */
    protected Probe getReadinessProbe(ContainerBuilder containerBuilder) {
        // return a generic probe by default
        return getReadinessProbe();
    }

    /**
     * Override this method to create a generic readiness probe.
     */
    protected Probe getReadinessProbe() {
        return null;
    }

    /**
     * Override this method to create a per-container liveness probe.
     */
    protected Probe getLivenessProbe(ContainerBuilder containerBuilder) {
        // return a generic probe by default
        return getLivenessProbe();
    }

    /**
     * Override this method to create a generic liveness probe.
     */
    protected Probe getLivenessProbe() {
        return null;
    }

}
