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

package io.fabric8.maven.enricher.standard;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressBackendBuilder;
import io.fabric8.kubernetes.api.model.extensions.IngressBuilder;
import io.fabric8.kubernetes.api.model.extensions.IngressSpecBuilder;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.util.kubernetes.KubernetesResourceUtil;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.Kind;
import io.fabric8.maven.enricher.api.MavenEnricherContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Enricher which generates an Ingress for each exposed Service
 */

public class IngressEnricher extends BaseEnricher {

    private Set<Integer> webPorts = new HashSet<>(Arrays.asList(80, 443, 8080, 9090));

    public IngressEnricher(MavenEnricherContext buildContext) {
        super(buildContext, "fmp-ingress");
    }

    @Override
    public void create(PlatformMode platformMode, final KubernetesListBuilder listBuilder) {
        if (platformMode == PlatformMode.kubernetes && !KubernetesResourceUtil.checkForKind(listBuilder, Kind.INGRESS.name())) {
            final List<Ingress> ingresses = new ArrayList<>();
            listBuilder.accept(new TypedVisitor<ServiceBuilder>() {
                @Override
                public void visit(ServiceBuilder serviceBuilder) {
                    addIngress(listBuilder, serviceBuilder);
                }
            });

        }
    }

    private void addIngress(KubernetesListBuilder listBuilder, ServiceBuilder serviceBuilder) {
        ObjectMeta metadata = serviceBuilder.getMetadata();
        if (metadata != null) {
            String name = metadata.getName();
            Integer servicePort = getServicePort(serviceBuilder);
            if (servicePort != null) {
                ResourceConfig resourceConfig = getConfiguration().getResource().orElse(null);

                IngressBuilder ingressBuilder = new IngressBuilder().
                        withMetadata(serviceBuilder.getMetadata()).
                        withNewSpec().
                        endSpec();
                IngressSpecBuilder specBuilder = new IngressSpecBuilder().withBackend(new IngressBackendBuilder().
                        withNewServiceName(name).
                        withNewServicePort(getServicePort(serviceBuilder)).
                        build());
                if (resourceConfig != null) {
                    specBuilder.addAllToRules(resourceConfig.getIngressRules());
                }
            }
        }
    }

    private Integer getServicePort(ServiceBuilder serviceBuilder) {
        ServiceSpec spec = serviceBuilder.getSpec();
        if (spec != null) {
            List<ServicePort> ports = spec.getPorts();
            if (ports != null && ports.size() > 0 && hasWebPort(ports)) {
                for (ServicePort port : ports) {
                    if (port.getName().equals("http") || port.getProtocol().equals("http")) {
                        return port.getPort();
                    }
                }
                ServicePort servicePort = ports.get(0);
                if (servicePort != null) {
                    return servicePort.getPort();
                }
            }
        }
        return null;
    }


    private boolean hasWebPort(List<ServicePort> ports) {
        for (ServicePort port : ports) {
            Integer portNumber = port.getPort();
            if (portNumber != null) {
                if (webPorts.contains(portNumber)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if we already have a route created for the given name
     */
    private boolean hasIngress(final KubernetesListBuilder listBuilder, final String name) {
        final AtomicBoolean answer = new AtomicBoolean(false);
        listBuilder.accept(new TypedVisitor<IngressBuilder>() {

            @Override
            public void visit(IngressBuilder builder) {
                ObjectMeta metadata = builder.getMetadata();
                if (metadata != null && name.equals(metadata.getName())) {
                    answer.set(true);
                }
            }
        });
        return answer.get();
    }
}