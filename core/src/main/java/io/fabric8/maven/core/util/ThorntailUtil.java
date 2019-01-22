/**
 * Copyright 2018 Red Hat, Inc.
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

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;

public class ThorntailUtil {

    /**
     * Returns the thorntail configuration (supports `project-defaults.yml`)
     * or an empty properties object if not found
     */
    public static Properties getThorntailProperties(URLClassLoader compileClassLoader) {
        URL ymlResource = compileClassLoader.findResource("project-defaults.yml");

        Properties props = YamlUtil.getPropertiesFromYamlResource(ymlResource);
        return props;
    }
}
