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

package io.fabric8.maven.enricher.standard.openshift;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.util.FileUtil;
import io.fabric8.maven.core.util.kubernetes.Fabric8Annotations;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.fabric8.openshift.api.model.RoutePort;
import org.apache.commons.lang3.StringUtils;

/**
 * Enricher which generates a Route for each exposed Service
 */
public class RouteEnricher extends BaseEnricher {
    private Boolean generateRoute;

    public RouteEnricher(MavenEnricherContext buildContext) {
        super(buildContext, "fmp-openshift-route");
        this.generateRoute = getValueFromConfig(GENERATE_ROUTE, true);
    }

    private String routeDomainPostfix;

    @Override
    public void create(PlatformMode platformMode, final KubernetesListBuilder listBuilder) {
        ResourceConfig resourceConfig = getConfiguration().getResource().orElse(null);

        if (resourceConfig != null && resourceConfig.getRouteDomain() != null) {
            routeDomainPostfix = resourceConfig.getRouteDomain();
        }

        if(platformMode == PlatformMode.openshift && generateRoute.equals(Boolean.TRUE)) {
            final List<Route> routes = new ArrayList<>();
            listBuilder.accept(new TypedVisitor<ServiceBuilder>() {

                @Override
                public void visit(ServiceBuilder serviceBuilder) {
                    addRoute(listBuilder, serviceBuilder, routes);
                }
            });

            if (!routes.isEmpty()) {
                Route[] routeArray = new Route[routes.size()];
                routes.toArray(routeArray);
                listBuilder.addToItems(routeArray);
            }
        }
    }


    private RoutePort createRoutePort(ServiceBuilder serviceBuilder) {
        RoutePort routePort = null;
        ServiceSpec spec = serviceBuilder.getSpec();
        if (spec != null) {
            List<ServicePort> ports = spec.getPorts();
            if (ports != null && ports.size() > 0) {
                ServicePort servicePort = ports.get(0);
                if (servicePort != null) {
                    IntOrString targetPort = servicePort.getTargetPort();
                    if (targetPort != null) {
                        routePort = new RoutePort();
                        routePort.setTargetPort(targetPort);
                    }
                }
            }
        }
        return routePort;
    }

    private String prepareHostForRoute(String routeDomainPostfix, String name) {
        String ret = FileUtil.stripPostfix(name,"-service");
        ret = FileUtil.stripPostfix(ret,".");
        ret += ".";
        ret += FileUtil.stripPrefix(routeDomainPostfix, ".");
        return ret;
    }

    private Set<Integer> getPorts(ServiceBuilder service) {
        Set<Integer> answer = new HashSet<>();
        if (service != null) {
            ServiceSpec spec = getOrCreateSpec(service);
            for (ServicePort port : spec.getPorts()) {
                answer.add(port.getPort());
            }
        }
        return answer;
    }

    public static ServiceSpec getOrCreateSpec(ServiceBuilder entity) {
        ServiceSpec spec = entity.getSpec();
        if (spec == null) {
            spec = new ServiceSpec();
            entity.editOrNewSpec().endSpec();
        }
        return spec;
    }

    private boolean hasExactlyOneServicePort(ServiceBuilder service, String id) {
        Set<Integer> ports = getPorts(service);
        if (ports.size() != 1) {
            log.info("Not generating route for service " + id + " as only single port services are supported. Has ports: " +
                    ports);
            return false;
        } else {
            return true;
        }
    }

    private void addRoute(KubernetesListBuilder listBuilder, ServiceBuilder serviceBuilder, List<Route> routes) {
        ObjectMeta serviceMetadata = serviceBuilder.getMetadata();

        if (serviceMetadata != null && StringUtils.isNotBlank(serviceMetadata.getName())
                && hasExactlyOneServicePort(serviceBuilder, serviceMetadata.getName()) && isExposedService(serviceMetadata)) {
            String name = serviceMetadata.getName();
            if (!hasRoute(listBuilder, name)) {
                if (StringUtils.isNotBlank(routeDomainPostfix)) {
                    routeDomainPostfix = prepareHostForRoute(routeDomainPostfix, name);
                } else {
                    routeDomainPostfix = "";
                }

                RoutePort routePort = createRoutePort(serviceBuilder);
                if (routePort != null) {
                    // TODO one day lets support multiple ports on a Route when the model supports it
                    RouteBuilder routeBuilder = new RouteBuilder().
                            withMetadata(serviceMetadata).
                            withNewSpec().
                            withPort(routePort).
                            withNewTo().withKind("Service").withName(name).endTo().
                            withHost(routeDomainPostfix.isEmpty() ? null : routeDomainPostfix).
                            endSpec();

                    // removing `expose : true` label from metadata.
                    routeBuilder.withNewMetadataLike(removeExposeLabel(routeBuilder.getMetadata()));
                    routes.add(routeBuilder.build());
                }
            }
        }
    }

    /**
     * Returns true if we already have a route created for the given name
     */
    private boolean hasRoute(final KubernetesListBuilder listBuilder, final String name) {
        final AtomicBoolean answer = new AtomicBoolean(false);
        listBuilder.accept(new TypedVisitor<RouteBuilder>() {

            @Override
            public void visit(RouteBuilder builder) {
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

}
