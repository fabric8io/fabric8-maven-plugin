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
package io.fabric8.maven.core.config;

import java.util.Collections;
import java.util.List;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author roland
 * @since 22/03/16
 */
public class ServiceConfig {

    // Service name
    @Parameter
    private String name;

    // Ports to expose
    @Parameter
    List<Port> ports;

    // Whether this is a headless service
    @Parameter
    private boolean headless = false;

    // If the expose label is added to the service
    @Parameter
    private boolean expose = false;

    // Service type
    @Parameter
    private String type;

    public String getName() {
        return name;
    }

    public List<Port> getPorts() {
        return ports != null ? ports : Collections.<Port>emptyList();
    }

    public boolean isHeadless() {
        return headless;
    }

    public boolean isExpose() {
        return expose;
    }

    public String getType() {
        return type;
    }

    // =============================================================

    public static class Builder {
        private ServiceConfig config = new ServiceConfig();

        public Builder name(String name) {
            config.name = name;
            return this;
        }

        public Builder ports(List<Port> ports) {
            config.ports = ports;
            return this;
        }

        public Builder headless(boolean headless) {
            config.headless = headless;
            return this;
        }

        public Builder expose(boolean expose) {
            config.expose = expose;
            return this;
        }

        public Builder type(String type) {
            config.type = type;
            return this;
        }

        public ServiceConfig build() {
            return config;
        }
    }


    // =============================================================

    public static class Port {

        // Protocol to use. Can be either "tcp" or "udp"
        @Parameter
        String protocol;

        // Container port to expose
        @Parameter
        int port;

        // Target port to expose
        @Parameter
        int targetPort;

        // Port to expose from the port
        @Parameter
        Integer nodePort;

        // Name of the port
        @Parameter
        String name;

        public ServiceProtocol getProtocol() {
            return protocol != null ? ServiceProtocol.valueOf(protocol.toUpperCase()) : null;
        }

        public int getPort() {
            return port;
        }

        public int getTargetPort() {
            return targetPort;
        }

        public Integer getNodePort() {
            return nodePort;
        }

        public String getName() {
            return name;
        }

        // =====================================================================================

        public static class Builder {

            Port config = new Port();

            public static Builder from(Port port) {
                Builder ret = new Builder();
                ret.config = port;
                return ret;
            }

            public Builder name(String name) {
                config.name = name;
                return this;
            }

            public Builder protocol(ServiceProtocol protocol) {
                config.protocol = protocol != null ? protocol.name() : null;
                return this;
            }

            public Builder protocol(String protocol) {
                config.protocol = protocol;
                return this;
            }

            public Builder port(int port) {
                config.port = port;
                return this;
            }

            public Builder targetPort(int targetPort) {
                config.targetPort = targetPort;
                return this;
            }

            public Builder nodePort(Integer nodePort) {
                config.nodePort = nodePort;
                return this;
            }

            public Port build() {
                return config;
            }
        }
    }
}
