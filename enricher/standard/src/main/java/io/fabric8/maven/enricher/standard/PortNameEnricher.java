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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;

/**
 * Enrich container ports with names with names of IANA registered services, if not already present.
 */
public class PortNameEnricher extends BaseEnricher {
    public PortNameEnricher(EnricherContext buildContext) {
        super(buildContext, "fmp-portname");
    }

    public static final Map<Integer, String> DEFAULT_PORT_MAPPING;
    static {
        Map<Integer, String> temp = new HashMap<>();
        temp.put(8080, "http");
        temp.put(8443, "https");
        temp.put(8778, "jolokia");
        temp.put(9779, "prometheus");
        DEFAULT_PORT_MAPPING = Collections.unmodifiableMap(temp);
    }

    @Override
    public void addMissingResources(KubernetesListBuilder builder) {
        builder.accept(new TypedVisitor<ContainerPortBuilder>() {
            @Override
            public void visit(ContainerPortBuilder builder) {
                if (builder.getContainerPort() != null && (builder.getName() == null || builder.getName().isEmpty())) {
                    String serviceName = DEFAULT_PORT_MAPPING.get(builder.getContainerPort());
                    if (serviceName != null && !serviceName.isEmpty()) {
                        log.verbose("Adding port name %s for port %d", serviceName, builder.getContainerPort());
                        builder.withName(serviceName);
                    }
                }
            }
        });
    }

}
