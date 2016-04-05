package io.fabric8.maven.fabric8.config;
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

import java.util.List;

import io.fabric8.utils.Ports;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author roland
 * @since 22/03/16
 */
public class ServiceConfiguration {

    // Service name
    @Parameter
    private String name;

    // Ports to expos
    @Parameter
    List<Port> ports;

    // =============================================================

    private class Port {

        // Protocol to use. Can be either "tcp" or "udp"
        @Parameter
        ServiceProtocol protocol;

        // Container port to expose
        @Parameter
        int port;

        // Target port to expose
        @Parameter
        int targetPort;
    }
}
