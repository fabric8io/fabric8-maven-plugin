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
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPathBuilder;
import io.fabric8.kubernetes.api.model.extensions.IngressBackendBuilder;
import io.fabric8.kubernetes.api.model.extensions.IngressBuilder;
import io.fabric8.kubernetes.api.model.extensions.IngressSpecBuilder;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.util.FileUtil;
import io.fabric8.maven.core.util.kubernetes.Fabric8Annotations;
import io.fabric8.maven.core.util.kubernetes.KubernetesHelper;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Enricher which generates an Ingress for each exposed Service
 */

public class IngressEnricher extends BaseEnricher {

    public IngressEnricher(MavenEnricherContext buildContext) {
        super(buildContext, "fmp-ingress");
    }

    private String routeDomainPostfix;

    @Override
    public void create(PlatformMode platformMode, final KubernetesListBuilder listBuilder) {
        ResourceConfig resourceConfig = getConfiguration().getResource().orElse(null);

        if (resourceConfig != null && resourceConfig.getRouteDomain() != null) {
            routeDomainPostfix = resourceConfig.getRouteDomain();
        }

        if (platformMode == PlatformMode.kubernetes) {
            listBuilder.accept(new TypedVisitor<ServiceBuilder>() {
                @Override
                public void visit(ServiceBuilder serviceBuilder) {
                    addIngress(listBuilder, serviceBuilder);
                }
            });
        }
    }

    private void addIngress(KubernetesListBuilder listBuilder, ServiceBuilder serviceBuilder) {
        ObjectMeta serviceMetadata = serviceBuilder.getMetadata();
        if (serviceMetadata != null && isExposedService(serviceMetadata) && shouldCreateExternalURLForService(serviceBuilder)) {
            String serviceName = serviceMetadata.getName();
            if (!hasIngress(listBuilder, serviceName)) {
                Integer servicePort = getServicePort(serviceBuilder);
                if (servicePort != null) {

                    IngressBuilder ingressBuilder = new IngressBuilder().
                            withMetadata(serviceMetadata).
                            withNewSpec().
                            endSpec();

                    // removing `expose : true` label from metadata.
                    ingressBuilder.withNewMetadataLike(removeExposeLabel(ingressBuilder.getMetadata()));

                    if (StringUtils.isNotBlank(routeDomainPostfix)) {
                        routeDomainPostfix = serviceName + "." + FileUtil.stripPrefix(routeDomainPostfix, ".");
                        ingressBuilder = ingressBuilder.withSpec(new IngressSpecBuilder().addNewRule().
                                withHost(routeDomainPostfix).
                                withNewHttp().
                                withPaths(new HTTPIngressPathBuilder()
                                        .withNewBackend()
                                        .withServiceName(serviceName)
                                        .withServicePort(KubernetesHelper.createIntOrString(getServicePort(serviceBuilder)))
                                        .endBackend()
                                        .build())
                                .endHttp().
                                        endRule().build());
                    } else {
                        ingressBuilder.withSpec(new IngressSpecBuilder().withBackend(new IngressBackendBuilder().
                                withNewServiceName(serviceName)
                                .withNewServicePort(getServicePort(serviceBuilder))
                                .build()).build());
                    }

                    listBuilder.addToIngressItems(ingressBuilder.build());
                }
            }
        }
    }

    private Integer getServicePort(ServiceBuilder serviceBuilder) {
        ServiceSpec spec = serviceBuilder.getSpec();
        if (spec != null) {
            List<ServicePort> ports = spec.getPorts();
            if (ports != null && ports.size() > 0) {
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

    private ObjectMeta removeExposeLabel(ObjectMeta metadata) {
        Map<String, String> labels = null;
        if (metadata != null) {
            labels = metadata.getLabels();
            if (labels != null) {
                if ("true".equals(labels.get("expose"))) {
                    labels.remove("expose");
                }
                if("true".equals(labels.get(Fabric8Annotations.SERVICE_EXPOSE_URL.value()))) {
                    labels.remove(Fabric8Annotations.SERVICE_EXPOSE_URL.value());
                }
            }
        }
        return metadata;
    }

    private boolean isExposedService(ObjectMeta metadata) {
        if (metadata != null) {
            Map<String, String> labels = metadata.getLabels();
            if (labels != null) {
                if ("true".equals(labels.get("expose")) || "true".equals(labels.get(Fabric8Annotations.SERVICE_EXPOSE_URL.value()))) {
                    return true;
                }
            }
        } else {
            log.info("No Metadata for service! " + metadata.getName());
        }
        return false;
    }

    /**
     * Should we try to create an external URL for the given service?
     * <p/>
     * By default lets ignore the kubernetes services and any service which does not expose ports 80 and 443
     *
     * @return true if we should create an Ingress for this service.
     */
    private boolean shouldCreateExternalURLForService(ServiceBuilder service) {
        String serviceName = service.getMetadata().getName();
        if ("kubernetes".equals(serviceName) || "kubernetes-ro".equals(serviceName)) {
            return false;
        }
        ServiceSpec spec = service.getSpec();
        List<ServicePort> ports = spec.getPorts();
        log.debug("Service " + serviceName + " has ports: " + ports);
        if (ports.size() == 1) {
            String type = null;
            if (spec != null) {
                type = spec.getType();
                if (Objects.equals(type, "LoadBalancer")) {
                    return true;
                }
            }
            log.info("Not generating Ingress for service " + serviceName + " type is not LoadBalancer: " + type);
            return false;
        } else {
            log.info("Not generating Ingress for service " + serviceName + " as only single port services are supported. Has ports: " + ports);
            return false;
        }
    }
}