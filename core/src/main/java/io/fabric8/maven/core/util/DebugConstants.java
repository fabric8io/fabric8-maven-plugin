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
 * Some constants for Java Debugging on Kubernetes
 */
public class DebugConstants {
    public static final String ENV_VAR_JAVA_DEBUG = "JAVA_ENABLE_DEBUG";
    public static final String ENV_VAR_JAVA_DEBUG_SUSPEND = "JAVA_DEBUG_SUSPEND";
    public static final String ENV_VAR_JAVA_DEBUG_SESSION = "JAVA_DEBUG_SESSION";
    public static final String ENV_VAR_JAVA_DEBUG_PORT = "JAVA_DEBUG_PORT";
    public static final String ENV_VAR_JAVA_DEBUG_PORT_DEFAULT = "5005";
}
