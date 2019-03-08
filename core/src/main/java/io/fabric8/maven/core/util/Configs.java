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
package io.fabric8.maven.core.util;

import java.util.Properties;

/**
 * Helper functions for working with typesafe configs
 */
public class Configs {

    // Interfaces to use for dealing with configuration values and default values
    public interface Key {
        String name();
        String def();
    }

    public static int asInt(String value) {
        return value != null ? Integer.parseInt(value) : 0;
    }

    public static Integer asInteger(String value) {
        return value != null ? Integer.parseInt(value) : null;
    }

    public static boolean asBoolean(String value) {
        return value != null ? Boolean.parseBoolean(value) : false;
    }

    public static String asString(String value) { return value; }

    public static String getPropertyWithSystemAsFallback(Properties properties, String key) {
        String val = null;
        if (properties != null) {
            val = properties.getProperty(key);
        }
        if (val == null) {
            val = System.getProperty(key);
        }
        return val;
    }
}
