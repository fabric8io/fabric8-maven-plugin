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

/**
 * Standard properties from Spring Boot <code><application.properties/code> files
 */
public class SpringBootProperties {
    public static final String MANAGEMENT_PORT = "management.port";
    public static final String SERVER_PORT = "server.port";
    public static final String SERVER_KEYSTORE = "server.ssl.key-store";
    public static final String DEV_TOOLS_REMOTE_SECRET = "spring.devtools.remote.secret";
    public static final String DEV_TOOLS_REMOTE_SECRET_ENV = "SPRING_DEVTOOLS_REMOTE_SECRET";
    public static final String CONTEXT_PATH = "server.context-path";
    public static final String SPRING_BOOT_GROUP_ID = "org.springframework.boot";
    public static final String SPRING_BOOT_ARTIFACT_ID = "spring-boot";
    public static final String SPRING_BOOT_DEVTOOLS_ARTIFACT_ID = "spring-boot-devtools";
}
