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
import java.util.Map;

import io.fabric8.maven.docker.access.DockerAccessException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.plugin.customizer.ImageConfigCustomizerManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Proxy to d-m-p's build Mojo
 *
 * @author roland
 * @since 16/03/16
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.INSTALL)
public class BuildMojo extends io.fabric8.maven.docker.BuildMojo {

    @Parameter
    Map<String, String> customizer;

    @Override
    protected void executeInternal(ServiceHub hub) throws DockerAccessException, MojoExecutionException {
        super.executeInternal(hub);
    }

    @Override
    public List<ImageConfiguration> customizeConfig(List<ImageConfiguration> configs) {
        return ImageConfigCustomizerManager.customize(configs, customizer, project);
    }

    @Override
    protected String getLogPrefix() {
        return "F8> ";
    }
}
