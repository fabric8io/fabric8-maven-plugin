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

package io.fabric8.maven.enricher.standard;

import java.io.IOException;
import java.util.Set;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;

import static io.fabric8.ianaservicehelper.Helper.serviceNames;

/**
 * Enrich container ports with names with names of IANA registered services, if not already present.
 */
public class IANAServicePortNameEnricher extends BaseEnricher {
    public IANAServicePortNameEnricher(EnricherContext buildContext) {
        super(buildContext, "fmp-ianaservice");
    }

    @Override
    public void addMissingResources(KubernetesListBuilder builder) {
        builder.accept(new TypedVisitor<ContainerPortBuilder>() {
            @Override
            public void visit(ContainerPortBuilder builder) {
                if (builder.getContainerPort() != null && (builder.getName() == null || builder.getName().isEmpty())) {
                    String protocol = builder.getProtocol();
                    if (protocol == null || protocol.isEmpty()) {
                        protocol = "tcp";
                    }
                    try {
                        Set<String> sn = serviceNames(builder.getContainerPort(), protocol.toLowerCase());
                        if (sn != null && !sn.isEmpty()) {
                            String serviceName = sn.iterator().next();
                            log.verbose("Adding port name %s for port %d", serviceName, builder.getContainerPort());
                            builder.withName(serviceName);
                        }
                    } catch (IOException e) {
                        log.warn("Failed to find service names for port %d/%s : %s", builder.getContainerPort(), protocol.toLowerCase(), e.getMessage());
                    }
                }
            }
        });
    }

}
