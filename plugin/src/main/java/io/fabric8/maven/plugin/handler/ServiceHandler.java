package io.fabric8.maven.plugin.handler;
/*
 * 
 * Copyright 2016 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.*;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.maven.plugin.config.AnnotationConfiguration;
import io.fabric8.maven.plugin.config.KubernetesConfiguration;
import io.fabric8.maven.plugin.config.ServiceConfiguration;
import io.fabric8.maven.enricher.api.Kind;
import io.fabric8.utils.Strings;

/**
 * @author roland
 * @since 08/04/16
 */
public class ServiceHandler {

    private final LabelHandler labelHandler;

    public ServiceHandler(LabelHandler labelHandler) {
        this.labelHandler = labelHandler;
    }

    public Service[] getServices(KubernetesConfiguration config) {

        ArrayList<Service> ret = new ArrayList<>();

        List<ServiceConfiguration> services = config.getServices();
        for (ServiceConfiguration service : services) {
            AnnotationConfiguration annos = config.getAnnotations();
            Map<String, String> serviceAnnotations = annos != null ? annos.getService() : null;

            Map<String, String> labels = labelHandler.extractLabels(Kind.SERVICE, config);
            Map<String, String> selector = new HashMap<>(labels);

            ServiceBuilder serviceBuilder = new ServiceBuilder()
                .withNewMetadata()
                  .withName(service.getName())
                  .withLabels(labels)
                  .withAnnotations(serviceAnnotations)
                .endMetadata();

            ServiceFluent.SpecNested<ServiceBuilder> serviceSpecBuilder =
                serviceBuilder.withNewSpec().withSelector(selector);

            List<ServicePort> servicePorts = new ArrayList<>();
            for (ServiceConfiguration.Port port : service.getPorts()) {
                ServicePort servicePort = new ServicePortBuilder()
                    .withProtocol(port.getProtocol().name())
                    .withTargetPort(new IntOrString(port.getTargetPort()))
                    .withPort(port.getPort())
                    .withNodePort(port.getNodePort())
                    .build();
                servicePorts.add(servicePort);
            }

            if (!servicePorts.isEmpty()) {
                serviceSpecBuilder.withPorts(servicePorts);
            }

            if (service.isHeadless()) {
                serviceSpecBuilder.withClusterIP("None");
            }

            if (!Strings.isNullOrBlank(service.getType())) {
                serviceSpecBuilder.withType(service.getType());
            }
            serviceSpecBuilder.endSpec();

            if (service.isHeadless() || !servicePorts.isEmpty()) {
                ret.add(serviceBuilder.build());
            }
        }
        return ret.toArray(new Service[ret.size()]);
    }

}
