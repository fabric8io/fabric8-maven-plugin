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

package io.fabric8.maven.plugin.customizer;

import java.util.List;
import java.util.Map;

import io.fabric8.maven.core.util.PluginServiceFactory;
import io.fabric8.maven.customizer.api.Customizer;
import io.fabric8.maven.customizer.api.MavenCustomizerContext;
import io.fabric8.maven.docker.config.ImageConfiguration;
import org.apache.maven.project.MavenProject;

/**
 * @author roland
 * @since 15/05/16
 */
public class ImageConfigCustomizerManager {

    public static List<ImageConfiguration> customize(List<ImageConfiguration> imageConfigs,
                                                     Map<String, String> customizerConfigs,
                                                     MavenProject project) {

        List<ImageConfiguration> ret = imageConfigs;
        PluginServiceFactory<MavenCustomizerContext> pluginFactory = new PluginServiceFactory<>(
            new MavenCustomizerContext(project));
        List<Customizer> customizers =
            pluginFactory.createServiceObjects("META-INF/fabric8-customizer-default", "META-INF/fabric8-customizer");
        for (Customizer customizer : customizers) {
            ret = customizer.customize(ret);
        }
        return ret;
    }
}
