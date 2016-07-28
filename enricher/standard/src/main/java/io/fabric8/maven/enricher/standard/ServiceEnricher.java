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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.config.ServiceConfig;
import io.fabric8.maven.core.config.ServiceProtocol;
import io.fabric8.maven.core.handler.HandlerHub;
import io.fabric8.maven.core.handler.ServiceHandler;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.utils.Strings;
import org.apache.maven.shared.utils.StringUtils;

/**
 * An enricher for creating default services when not present.
 *
 * @author roland
 * @since 25/05/16
 */
public class ServiceEnricher extends BaseEnricher {

    ServiceHandler serviceHandler;

    // Available configuration keys
    private enum Config implements Configs.Key {
        // Default name to use instead of a calculated one
        name,

        // Whether allow headless services.
        headless {{ d = "false"; }},

        // Type of the service (LoadBalancer, NodePort, ...)
        type {{ d = "LoadBalancer"; }};

        public String def() { return d; } protected String d;
    }

    public ServiceEnricher(EnricherContext buildContext) {
        super(buildContext, "f8-service");
        HandlerHub handlers = new HandlerHub(buildContext.getProject());
        serviceHandler = handlers.getServiceHandler();
    }

    @Override
    public void addMissingResources(KubernetesListBuilder builder) {
        final ServiceConfig defaultServiceConfig = extractDefaultServiceConfig();
        final Service defaultService = serviceHandler.getService(defaultServiceConfig,null);

        if (hasServices(builder)) {
            builder.accept(new TypedVisitor<ServiceBuilder>() {
                @Override
                public void visit(ServiceBuilder service) {
                    mergeServices(service, defaultService);
                }
            });
        } else {
            if (defaultService != null) {
                ServiceSpec spec = defaultService.getSpec();
                if (spec != null) {
                    List<ServicePort> ports = spec.getPorts();
                    if (ports != null) {
                        log.info("Adding a default Service with ports [%s]", formatPortsAsList(ports));
                        builder.addToServiceItems(defaultService);
                    }
                }
            }
        }
    }

    // Check for all build configs, extract the exposed ports and create a single service for all of them
    private ServiceConfig extractDefaultServiceConfig() {
        List<ServiceConfig.Port> ports = extractPortsFromImageConfiguration(getImages());

        ServiceConfig.Builder ret = new ServiceConfig.Builder();
        ret.name(getConfig(Config.name, MavenUtil.createDefaultResourceName(getProject())));

        if (ports.size() > 0) {
            ret.ports(ports);
        } else {
            if (Configs.asBoolean(getConfig(Config.headless))) {
                ret.headless(true);
            }
        }

        // Add a default type if configured
        ret.type(getConfig(Config.type));

        return ret.build();
    }


    // Examine images for build configuration and extract all ports
    private List<ServiceConfig.Port> extractPortsFromImageConfiguration(List<ImageConfiguration> images) {
        List<ServiceConfig.Port> ret = new ArrayList<>();
        for (ImageConfiguration image : images) {
            BuildImageConfiguration buildConfig = image.getBuildConfiguration();
            if (buildConfig != null) {
                List<String> ports = buildConfig.getPorts();
                if (ports != null) {
                    for (String port : ports) {
                        /// Todo: Check IANA names (also in case port is not numeric)
                        int portI = Integer.parseInt(port);
                        ret.add(
                            new ServiceConfig.Port.Builder()
                                .protocol(ServiceProtocol.TCP) // TODO: default for the moment
                                .port(portI)
                                .targetPort(portI)
                                .build()
                               );
                    }
                }
            }
        }
        return ret;
    }

    private String formatPortsAsList(List<ServicePort> ports)  {
        List<String> p = new ArrayList<>();
        for (ServicePort port : ports) {
            IntOrString targetPort = port.getTargetPort();
            String val = targetPort.getStrVal();
            if (val == null) {
                val = Integer.toString(targetPort.getIntVal());
            }
            p.add(val);
        }
        return StringUtils.join(p.iterator(), ",");
    }

    private void mergeServices(ServiceBuilder loadedService, Service defaultService) {
        String defaultServiceName = KubernetesHelper.getName(defaultService);
        if (Strings.isNullOrBlank(defaultServiceName)) {
            defaultServiceName = getProject().getArtifactId();
        }
        ObjectMeta loadedMetadata = loadedService.getMetadata();
        if (loadedMetadata == null) {
            loadedMetadata = defaultService.getMetadata();
            loadedService.withNewMetadataLike(loadedMetadata).endMetadata();
        }
        String name = KubernetesHelper.getName(loadedMetadata);
        if (Strings.isNullOrBlank(name)) {
            loadedService.withNewMetadataLike(loadedMetadata).withName(defaultServiceName).endMetadata();
            name = KubernetesHelper.getName(loadedService.getMetadata());
        }
        if (defaultService == null) {
            return;
        }

        // lets find a suitable service to match against
        if (Objects.equals(name, defaultServiceName)) {
            ServiceSpec matchedSpec = defaultService.getSpec();
            if (matchedSpec != null) {
                ServiceFluent.SpecNested<ServiceBuilder> loadedSpec = loadedService.editSpec();
                if (loadedSpec == null) {
                    loadedService.withNewSpecLike(matchedSpec).endSpec();
                } else {
                    List<ServicePort> ports = loadedSpec.getPorts();
                    if (ports == null || ports.isEmpty()) {
                        loadedSpec.withPorts(matchedSpec.getPorts()).endSpec();
                    } else {
                        // lets default any missing values
                        for (ServicePort port : ports) {
                            if (Strings.isNullOrBlank(port.getProtocol())) {
                                port.setProtocol("TCP");
                            }
                            if (Strings.isNullOrBlank(port.getName())) {
                                port.setName(getDefaultPortName(port));
                            }
                        }
                        // lets add first missing port
                        List<ServicePort> matchedPorts = matchedSpec.getPorts();
                        if (matchedPorts != null && ports.isEmpty()) {
                            for (ServicePort matchedPort : matchedPorts) {
                                if (!hasPort(ports, matchedPort)) {
                                    ports.add(matchedPort);
                                    // lets only add 1 port
                                    break;
                                }
                            }
                        }
                        loadedSpec.withPorts(ports).endSpec();
                    }
                }
            }
        }
    }

    public static String getDefaultPortName(ServicePort servicePort) {
        Integer port = servicePort.getPort();
        if (port != null) {
            switch (port) {
                case 80:
                case 8080:
                case 9090:
                    return "http";
                case 443:
                    return "https";
                case 8778:
                    return "jolokia";
                case 9779:
                    return "prometheus";
            }
        }
        return "default";
    }

    /**
     * Returns true if the given port can be found in the collection
     */
    private boolean hasPort(List<ServicePort> ports, ServicePort port) {
        for (ServicePort aPort : ports) {
            if (Objects.equals(port.getPort(), aPort.getPort())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasServices(KubernetesListBuilder builder) {
        for (HasMetadata item : builder.getItems()) {
            if ("Service".equals(item.getKind())) {
                return true;
            }
        }
        return false;
    }

}
