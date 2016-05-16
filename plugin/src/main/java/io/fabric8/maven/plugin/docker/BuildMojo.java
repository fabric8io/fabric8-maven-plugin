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

package io.fabric8.maven.plugin.docker;


import java.util.List;

import io.fabric8.maven.customizer.api.Customizer;
import io.fabric8.maven.customizer.api.MavenCustomizeContext;
import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.plugin.util.PluginServiceFactory;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Proxy to d-m-p's build Mojo
 *
 * @author roland
 * @since 16/03/16
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.INSTALL)
public class BuildMojo extends io.fabric8.maven.docker.BuildMojo {

    @Override
    protected void executeInternal(ServiceHub hub) throws DockerAccessException, MojoExecutionException {
        super.executeInternal(hub);
    }

    @Override
    protected List<ImageConfiguration> customizeImageConfigurations(List<ImageConfiguration> configs) {
        List<ImageConfiguration> ret = configs;

        PluginServiceFactory<MavenCustomizeContext> pluginFactory = new PluginServiceFactory<>(new MavenCustomizeContext(project));
        List<Customizer> customizers =
            pluginFactory.createServiceObjects("META-INF/fabric8-customizer-default", "META-INF/fabric8-customizer");
        for (Customizer customizer : customizers) {
            ret = customizer.customize(ret);
        }
        return ret;
    }
}
