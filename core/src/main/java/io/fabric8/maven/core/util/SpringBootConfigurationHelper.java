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
package io.fabric8.maven.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standard properties from Spring Boot <code><application.properties/code> files
 */
public class SpringBootConfigurationHelper {

    private static final Logger LOG = LoggerFactory.getLogger(SpringBootConfigurationHelper.class);

    public static final String SPRING_BOOT_GROUP_ID = "org.springframework.boot";
    public static final String SPRING_BOOT_ARTIFACT_ID = "spring-boot";
    public static final String SPRING_BOOT_DEVTOOLS_ARTIFACT_ID = "spring-boot-devtools";
    public static final String DEV_TOOLS_REMOTE_SECRET = "spring.devtools.remote.secret";
    public static final String DEV_TOOLS_REMOTE_SECRET_ENV = "SPRING_DEVTOOLS_REMOTE_SECRET";

    /*
        Following are property keys for spring-boot-1 and their spring-boot-2 equivalent
     */
    private static final String[] MANAGEMENT_PORT = {"management.port", "management.server.port"};
    private static final String[] SERVER_PORT = {"server.port", "server.port"};
    private static final String[] SERVER_KEYSTORE = {"server.ssl.key-store", "server.ssl.key-store"};
    private static final String[] MANAGEMENT_KEYSTORE = {"management.ssl.key-store", "management.server.ssl.key-store"};
    private static final String[] SERVLET_PATH = {"server.servlet-path", "server.servlet.path"};
    private static final String[] SERVER_CONTEXT_PATH = {"server.context-path", "server.servlet.context-path"};
    private static final String[] MANAGEMENT_CONTEXT_PATH = {"management.context-path", "management.server.servlet.context-path"};
    private static final String[] ACTUATOR_BASE_PATH = {null, "management.endpoints.web.base-path"};
    private static final String[] ACTUATOR_DEFAULT_BASE_PATH = {"", "/actuator"};

    private int propertyOffset;

    public SpringBootConfigurationHelper(String springBootVersion) {
        this.propertyOffset = propertyOffset(springBootVersion);
    }

    public String getManagementPortPropertyKey() {
        return lookup(MANAGEMENT_PORT);
    }

    public String getServerPortPropertyKey() {
        return lookup(SERVER_PORT);
    }

    public String getServerKeystorePropertyKey() {
        return lookup(SERVER_KEYSTORE);
    }

    public String getManagementKeystorePropertyKey() {
        return lookup(MANAGEMENT_KEYSTORE);
    }

    public String getServletPathPropertyKey() {
        return lookup(SERVLET_PATH);
    }

    public String getServerContextPathPropertyKey() {
        return lookup(SERVER_CONTEXT_PATH);
    }

    public String getManagementContextPathPropertyKey() {
        return lookup(MANAGEMENT_CONTEXT_PATH);
    }

    public String getActuatorBasePathPropertyKey() {
        return lookup(ACTUATOR_BASE_PATH);
    }

    public String getActuatorDefaultBasePath() {
        return lookup(ACTUATOR_DEFAULT_BASE_PATH);
    }

    private String lookup(String[] keys) {
        return keys[propertyOffset];
    }

    private int propertyOffset(String springBootVersion) {
        Integer majorVersion = majorVersion(springBootVersion);
        int idx = majorVersion != null ? majorVersion - 1 : 0;
        idx = Math.min(idx, 1);
        idx = Math.max(idx, 0);
        return idx;
    }

    private Integer majorVersion(String version) {
        if (version != null) {
            try {
                return Integer.parseInt(version.substring(0, version.indexOf(".")));
            } catch (Exception e) {
                LOG.warn("Cannot spring boot major version from {}", version);
            }
        }
        return null;
    }


}
