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

package io.fabric8.maven.generator.api;

import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.project.MavenProject;

/**
 * @author roland
 * @since 15/05/16
 */
public class MavenGeneratorContext {
    private final MavenProject project;
    private final ProcessorConfig config;

    private final Logger log;
    private final PlatformMode mode;
    private final OpenShiftBuildStrategy strategy;

    public MavenGeneratorContext(MavenProject project, ProcessorConfig generatorConfig, Logger log,
                                 PlatformMode mode, OpenShiftBuildStrategy strategy) {
        this.project = project;
        this.config = generatorConfig;
        this.log = log;
        this.mode = mode;
        this.strategy = strategy;
    }

    public MavenProject getProject() {
        return project;
    }

    public ProcessorConfig getConfig() {
        return config;
    }

    public Logger getLog() {
        return log;
    }

    public PlatformMode getMode() {
        return mode;
    }

    public OpenShiftBuildStrategy getStrategy() {
        return strategy;
    }
}
