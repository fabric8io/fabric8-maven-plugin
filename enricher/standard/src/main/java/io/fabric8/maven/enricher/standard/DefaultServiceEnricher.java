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

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.maven.core.config.ServiceConfig;
import io.fabric8.maven.core.config.ServiceProtocol;
import io.fabric8.maven.core.handler.HandlerHub;
import io.fabric8.maven.core.handler.ServiceHandler;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.utils.Strings;
import org.apache.maven.shared.utils.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.fabric8.ianaservicehelper.Helper;

/**
 * An enricher for creating default services when not present.
 *
 * @author roland
 * @since 25/05/16
 */
public class DefaultServiceEnricher extends BaseEnricher {

    private ServiceHandler serviceHandler;

    private static final Pattern PORT_PROTOCOL_PATTERN =
        Pattern.compile("^(\\d+)(/(?:tcp|udp))?$", Pattern.CASE_INSENSITIVE);


    // Available configuration keys
    private enum Config implements Configs.Key {
        // Default name to use instead of a calculated one
        name,

        // Whether allow headless services.
        headless {{ d = "false"; }},

        // Whether expose the service as ingress. Needs an 'exposeController'
        // running
        expose {{ d = "false"; }},

        // Type of the service (LoadBalancer, NodePort, ClusterIP, ...)
        type,

        // Service port to use (only for the first exposed pod port)
        port,

        // Legacy mapping from port 8080 / 9090 to 80
        legacyPortMapping {{ d = "false"; }},

        // protocol to use
        protocol {{ d = "tcp"; }};

        public String def() { return d; } protected String d;
    }

    public DefaultServiceEnricher(EnricherContext buildContext) {
        super(buildContext, "fmp-service");
        HandlerHub handlers = new HandlerHub(buildContext.getProject());
        serviceHandler = handlers.getServiceHandler();
    }

    @Override
    public void addMissingResources(KubernetesListBuilder builder) {
        final Service defaultService = getDefaultService();

        if (hasServices(builder)) {
            mergeInDefaultServiceParameters(builder, defaultService);
        } else if (defaultService != null) {
            addDefaultService(builder, defaultService);
        }
    }

    // =======================================================================================================

    private Service getDefaultService() {
        ServiceConfig serviceConfig = extractDefaultServiceConfig();
        return serviceConfig != null ? serviceHandler.getService(serviceConfig) : null;
    }

    private boolean hasServices(KubernetesListBuilder builder) {
        final AtomicBoolean hasService = new AtomicBoolean(false);
        builder.accept(new TypedVisitor<ServiceBuilder>() {
            @Override
            public void visit(ServiceBuilder element) {
                hasService.set(true);
            }
        });
        return hasService.get();
    }

    private void mergeInDefaultServiceParameters(KubernetesListBuilder builder, final Service defaultService) {
        builder.accept(new TypedVisitor<ServiceBuilder>() {
            @Override
            public void visit(ServiceBuilder service) {
                // Only update single service matching the default service's name

                String defaultServiceName = getDefaultServiceName(defaultService);
                ObjectMeta serviceMetadata = ensureServiceMetadata(service, defaultService);
                String serviceName = ensureServiceName(serviceMetadata, service, defaultServiceName);

                if (defaultService != null && defaultServiceName.equals(serviceName)) {
                    addMissingServiceParts(service, defaultService);
                }
            }
        });
    }

    private void addDefaultService(KubernetesListBuilder builder, Service defaultService) {
        ServiceSpec spec = defaultService.getSpec();
        if (spec != null) {
            List<ServicePort> ports = spec.getPorts();
            if (ports != null) {
                log.info("Adding a default service '%s' with ports [%s]",
                             defaultService.getMetadata().getName(),
                         formatPortsAsList(ports));
                builder.addToServiceItems(defaultService);
            }
        }
    }

    // ....................................................................................

    // Check for all build configs, extract the exposed ports and create a single service for all of them
    private ServiceConfig extractDefaultServiceConfig() {

        // No image config, no service
        if (!hasImageConfiguration()) {
            return null;
        }

        // Create service only for all images which are supposed to live in a single pod
        List<ServiceConfig.Port> ports = extractPortsFromImageConfigurations(getImages());
        if (ports == null) {
            return null;
        }
        ServiceConfig.Builder ret = new ServiceConfig.Builder();
        ret.name(getConfig(Config.name, MavenUtil.createDefaultResourceName(getProject())));

        if (ports.size() > 0) {
            ret.ports(ports);
        } else {
            if (Configs.asBoolean(getConfig(Config.headless))) {
                ret.headless(true);
            }
        }

        // Expose the service if desired
        ret.expose(Configs.asBoolean(getConfig(Config.expose)));

        // Add a default type if configured
        ret.type(getConfig(Config.type));

        return ret.build();
    }


    // Merge services of same name with the default service
    private void addMissingServiceParts(ServiceBuilder service, Service defaultService) {

        // If service has no spec -> take over the complete spec from default service
        if (!service.hasSpec()) {
            service.withNewSpecLike(defaultService.getSpec()).endSpec();
            return;
        }

        // If service has no ports -> take over ports from default service
        List<ServicePort> ports = service.buildSpec().getPorts();
        if (ports == null || ports.isEmpty()) {
            service.editSpec().withPorts(defaultService.getSpec().getPorts()).endSpec();
            return;
        }

        // Complete missing parts:
        service.editSpec()
                    .withPorts(addMissingDefaultPorts(ports, defaultService))
                    .endSpec();
    }

    private String ensureServiceName(ObjectMeta serviceMetadata, ServiceBuilder service, String defaultServiceName) {
        String serviceName = KubernetesHelper.getName(serviceMetadata);
        if (Strings.isNullOrBlank(serviceName)) {
            service.buildMetadata().setName(defaultServiceName);
            serviceName = KubernetesHelper.getName(service.buildMetadata());
        }
        return serviceName;
    }

    private ObjectMeta ensureServiceMetadata(ServiceBuilder service, Service defaultService) {
        if (!service.hasMetadata() && defaultService != null) {
            service.withNewMetadataLike(defaultService.getMetadata()).endMetadata();
        }
        return service.buildMetadata();
    }

    // ========================================================================================================
    // Port handling

    private List<ServicePort> addMissingDefaultPorts(List<ServicePort> ports, Service defaultService) {

        // Ensure protocol and port names on the given ports
        ensurePortProtocolAndName(ports);

        // lets add at least one default port
        return ensureAtLeastOnePort(ports, defaultService);
    }

    private void ensurePortProtocolAndName(List<ServicePort> ports) {
        for (ServicePort port : ports) {
            String protocol = ensureProtocol(port);
            ensurePortName(port, protocol);
        }
    }

    private List<ServicePort> ensureAtLeastOnePort(List<ServicePort> ports, Service defaultService) {
        List<ServicePort> defaultPorts = defaultService.getSpec().getPorts();
        if (!ports.isEmpty() || defaultPorts == null || defaultPorts.isEmpty()) {
            return ports;
        }
        return Collections.singletonList(defaultPorts.get(0));
    }

    private void ensurePortName(ServicePort port, String protocol) {
        if (Strings.isNullOrBlank(port.getName())) {
            port.setName(getDefaultPortName(port.getPort(), getProtocol(protocol)));
        }
    }

    private String ensureProtocol(ServicePort port) {
        String protocol = port.getProtocol();
        if (Strings.isNullOrBlank(protocol)) {
            port.setProtocol("TCP");
            return "TCP";
        }
        return protocol;
    }

    // Examine images for build configuration and extract all ports
    private List<ServiceConfig.Port> extractPortsFromImageConfigurations(List<ImageConfiguration> images) {
        List<ServiceConfig.Port> ret = new ArrayList<>();
        boolean firstPort = true;
        for (ImageConfiguration image : images) {
            // No build, no defaul service (doesn't make much sense to have no build config, though)
            BuildImageConfiguration buildConfig = image.getBuildConfiguration();
            if (buildConfig == null) {
                continue;
            }

            // No exposed ports, no default service
            List<String> podPorts = buildConfig.getPorts();
            if (podPorts == null) {
                continue;
            }

            Set<String> portNames = new HashSet<>();

            for (String port : podPorts) {
                Matcher portMatcher = PORT_PROTOCOL_PATTERN.matcher(port);
                if (!portMatcher.matches()) {
                    log.warn("Invalid port specification '%s' for image %s. Must match \\d+(/(tcp|udp))?. Ignoring for now for service generation",
                             port, image.getName());
                    continue;
                }
                int podPort = Integer.parseInt(portMatcher.group(1));
                ServiceProtocol serviceProtocol = getProtocol(portMatcher.group(2));

                int servicePort = extractServicePort(podPort, podPorts, firstPort);
                firstPort = false;

                String portName = getDefaultPortName(servicePort, serviceProtocol);

                if (!portNames.add(portName)) {
                    // Don't reuse service names
                    portName = null;
                }
                ret.add(
                    new ServiceConfig.Port.Builder()
                        .protocol(serviceProtocol)
                        .port(servicePort)
                        .targetPort(podPort)
                        .name(portName)
                        .build()
                       );
            }
        }
        return ret.isEmpty()? null : ret;
    }

    private ServiceProtocol getProtocol(String imageProtocol) {
        String protocol = imageProtocol != null ? imageProtocol : getConfig(Config.protocol);
        if ("tcp".equalsIgnoreCase(protocol)) {
            return ServiceProtocol.TCP;
        } else if ("udp".equalsIgnoreCase(protocol)) {
            return ServiceProtocol.UDP;
        } else {
            throw new IllegalArgumentException(
                String.format("Invalid service protocol %s specified for enricher '%s'. Must be 'tcp' or 'udp'",
                              protocol, getName()));
        }
    }

    private int extractServicePort(Integer podPort, List<String> portNumbers, boolean firstPort) {

        // Configured port takes precedence, but only if not already mapped
        String port = getConfig(Config.port);
        if (port != null && firstPort) {
            return Integer.parseInt(port);
        }

        // The legacy mapping maps 8080 -> 80 and 9090 -> 90 which will vanish
        if (!(portNumbers.contains("80") || portNumbers.contains("80/tcp")) &&
            (podPort == 8080 || podPort == 9090)) {
            if ("true".equals(getConfig(Config.legacyPortMapping))) {
                return 80;
            } else {
                // Temporary warning with hint what todo
                log.warn("Implicit service port mapping to port 80 has been disabled for the used port %d. " +
                         "To get back the old behaviour either use set the config port = 80 or use legacyPortMapping = true. " +
                         "See file:///Users/roland/Development/fabric8/fabric8-maven-plugin/doc/target/generated-docs/index.html#fmp-service for details.", podPort);
            }
        }

        // service port == pod port
        return podPort;
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

    private String getDefaultServiceName(Service defaultService) {
        String defaultServiceName = KubernetesHelper.getName(defaultService);
        if (Strings.isNullOrBlank(defaultServiceName)) {
            defaultServiceName = getProject().getArtifactId();
        }
        return defaultServiceName;
    }

    private String getDefaultPortName(int port, ServiceProtocol serviceProtocol) {
        if (serviceProtocol == ServiceProtocol.TCP) {
            switch (port) {
                case 80:
                case 8080:
                case 9090:
                    return "http";
                case 443:
                case 8443:
                    return "https";
                case 8778:
                    return "jolokia";
                case 9779:
                    return "prometheus";
            }
        }

        try {
            Set<String> serviceNames = Helper.serviceNames(port, serviceProtocol.toString().toLowerCase());
            if (serviceNames != null && !serviceNames.isEmpty()) {
                return serviceNames.iterator().next();
            } else {
                return null;
            }
        } catch (IOException e) {
            log.warn("Cannot lookup port %d/%s in IANA database: %s", port, serviceProtocol.toString().toLowerCase(), e.getMessage());
            return null;
        }
    }

}
