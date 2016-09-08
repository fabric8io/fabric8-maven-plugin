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

package io.fabric8.maven.enricher.api;

import java.util.List;

import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.project.MavenProject;

/**
 * @author roland
 * @since 01/04/16
 */
public class EnricherContext {

    private final MavenProject project;
    private final Logger log;

    private final List<ImageConfiguration> images;
    private final ResourceConfig resourceConfig;

    private ProcessorConfig config;

    private boolean useProjectClasspath;

    public EnricherContext(MavenProject project,
                           ProcessorConfig enricherConfig,
                           List<ImageConfiguration> images,
                           ResourceConfig kubernetesConfig,
                           Logger log,
                           boolean useProjectClasspath) {
        this.log = log;
        this.project = project;
        this.config = enricherConfig;
        this.images = images;
        this.resourceConfig = kubernetesConfig;
        this.useProjectClasspath = useProjectClasspath;
    }

    public MavenProject getProject() {
        return project;
    }

    public List<ImageConfiguration> getImages() {
        return images;
    }

    public Logger getLog() {
        return log;
    }

    public ProcessorConfig getConfig() {
        return config;
    }

    public ResourceConfig getResourceConfig() {
        return resourceConfig;
    }

    public boolean isUseProjectClasspath() {
        return useProjectClasspath;
    }
}
