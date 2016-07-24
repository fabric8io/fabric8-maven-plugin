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

import io.fabric8.maven.core.config.ProcessorConfiguration;
import org.apache.maven.project.MavenProject;

/**
 * @author roland
 * @since 15/05/16
 */
public class MavenGeneratorContext {
    private final MavenProject project;
    private final ProcessorConfiguration config;

    public MavenGeneratorContext(MavenProject project, ProcessorConfiguration config) {
        this.project = project;
        this.config = config;
    }

    protected MavenProject getProject() {
        return project;
    }

    public ProcessorConfiguration getConfig() {
        return config;
    }
}
