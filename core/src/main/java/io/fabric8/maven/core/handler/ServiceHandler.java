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

package io.fabric8.maven.core.handler;

import java.util.*;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.maven.core.config.ServiceConfiguration;
import io.fabric8.maven.core.util.KubernetesAnnotations;
import io.fabric8.maven.core.util.MapUtil;
import io.fabric8.utils.Strings;

import static org.bouncycastle.asn1.x500.style.RFC4519Style.name;

/**
 * @author roland
 * @since 08/04/16
 */
public class ServiceHandler {

    public Service getService(ServiceConfiguration service, Map<String, String> annotations) {
        List<Service> ret = getServices(Collections.singletonList(service),annotations);
        return ret.size() > 0 ? ret.get(0) : null;
    }

    public List<Service> getServices(List<ServiceConfiguration> services, Map<String, String> annotations) {

        ArrayList<Service> ret = new ArrayList<>();

        for (ServiceConfiguration service : services) {
            Map<String, String> serviceAnnotations = new HashMap<>();
            if (annotations != null) {
                serviceAnnotations.putAll(annotations);
            }
            // lets add the prometheus annotations if required
            String prometheusPort = findPrometheusPort(service.getPorts());
            if (Strings.isNotBlank(prometheusPort)) {
                        MapUtil.putIfAbsent(serviceAnnotations, KubernetesAnnotations.PROMETHEUS_PORT, prometheusPort);
                MapUtil.putIfAbsent(serviceAnnotations, KubernetesAnnotations.PROMETHEUS_SCRAPE, "true");
            }

            ServiceBuilder serviceBuilder = new ServiceBuilder()
                .withNewMetadata()
                  .withName(service.getName())
                  .withAnnotations(serviceAnnotations)
                .endMetadata();

            ServiceFluent.SpecNested<ServiceBuilder> serviceSpecBuilder =
                serviceBuilder.withNewSpec();

            List<ServicePort> servicePorts = new ArrayList<>();

            // lets default to only adding the first port as usually its the web port only
            // TODO we could add better filters maybe?
            // worst case folks can be specific of what ports to expose?
            int count = 0;
            for (ServiceConfiguration.Port port : service.getPorts()) {
                ServicePort servicePort = new ServicePortBuilder()
                    .withName(port.getName())
                    .withProtocol(port.getProtocol().name())
                    .withTargetPort(new IntOrString(port.getTargetPort()))
                    .withPort(port.getPort())
                    .withNodePort(port.getNodePort())
                    .build();
                servicePorts.add(servicePort);
                if (++count >= 1) {
                    break;
                }
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
        return ret;
    }

    private String findPrometheusPort(List<ServiceConfiguration.Port> ports) {
        for (ServiceConfiguration.Port port : ports) {
            int number = port.getPort();
            boolean valid = number == 9779;
            String name = port.getName();
            if (name != null && name.toLowerCase().equals("prometheus")) {
                valid = true;
            }
            if (valid) {
                return "" + number;
            }
        }
        return null;
    }

}
