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


import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.plugin.generator.GeneratorManager;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.util.List;
import java.util.Map;

/**
 * Proxy to d-m-p's watch Mojo
 */
@Mojo(name = "watch", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresDependencyResolution = ResolutionScope.COMPILE)
public class WatchMojo extends io.fabric8.maven.docker.WatchMojo {

    @Parameter
    Map<String, String> generator;

    @Override
    public List<ImageConfiguration> customizeConfig(List<ImageConfiguration> configs) {
        return GeneratorManager.generate(configs, generator, project, log);
    }

    @Override
    protected String getLogPrefix() {
        return "F8> ";
    }
}
