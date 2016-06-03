/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.maven.plugin;

import java.io.File;
import java.util.Properties;

import io.fabric8.kubernetes.api.Controller;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.client.*;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.utils.Strings;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static io.fabric8.kubernetes.api.KubernetesHelper.DEFAULT_NAMESPACE;

public abstract class AbstractFabric8Mojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    protected MavenSession session;

    // Logger to use
    protected Logger log = new AnsiLogger(getLog(), getBooleanConfigProperty("useColor",true), getBooleanConfigProperty("verbose", false), "F8> ");

    // Resolve properties with both `docker` (as used in d-m-p) and `fabric8` prefix
    protected boolean getBooleanConfigProperty(String key, boolean defaultVal) {
        Properties props = System.getProperties();
        for (String prefix : new String[] { "fabric8", "docker"}) {
            String lookup = prefix + "." + key;
            if (props.containsKey(lookup)) {
                return Boolean.parseBoolean(lookup);
            }
        }
        return defaultVal;
    }
}
