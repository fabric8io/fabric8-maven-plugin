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
import io.fabric8.maven.core.util.GoalFinder;
import io.fabric8.maven.core.util.OpenShiftDependencyResources;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;

/**
 * The context given to each enricher from where it can extract build specific information.
 *
 * @author roland
 * @since 01/04/16
 */
public class EnricherContext {

    private MavenProject project;
    private Logger log;

    private List<ImageConfiguration> images;
    private String namespace;

    private ProcessorConfig config = ProcessorConfig.EMPTY;

    private ResourceConfig resources;

    private boolean useProjectClasspath;
    private OpenShiftDependencyResources openshiftDependencyResources;
    private MavenSession session;
    private GoalFinder goalFinder;

    private EnricherContext() {}

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

    public ResourceConfig getResources() {
        return resources;
    }

    public String getNamespace() {
        return namespace;
    }

    public boolean isUseProjectClasspath() {
        return useProjectClasspath;
    }

    public Settings getSettings() {
        return session != null ? session.getSettings() : null;
    }

    public OpenShiftDependencyResources getOpenshiftDependencyResources() {
        return openshiftDependencyResources;
    }

    /**
     * Returns true if we are in watch mode
     */
    public boolean isWatchMode() {
        try {
            return runningWithGoal("fabric8:watch-spring-boot", "fabric8:watch");
        } catch (MojoExecutionException e) {
            throw new IllegalStateException("Cannot determine maven goals", e);
        }
    }

    /**
     * Returns true if maven is running with any of the given goals
     */
    public boolean runningWithGoal(String... goals) throws MojoExecutionException {
        for (String goal : goals) {
            if (goalFinder.runningWithGoal(project, session,  goal)) {
                return true;
            }
        }
        return false;
    }

    // =======================================================================================================
    public static class Builder {

        private EnricherContext ctx = new EnricherContext();

        public Builder session(MavenSession session) {
            ctx.session = session;
            return this;
        };

        public Builder goalFinder(GoalFinder goalFinder) {
            ctx.goalFinder = goalFinder;
            return this;
        }

        public Builder log(Logger log) {
            ctx.log = log;
            return this;
        }

        public Builder project(MavenProject project) {
            ctx.project = project;
            return this;
        }

        public Builder config(ProcessorConfig config) {
            ctx.config = config;
            return this;
        }

        public Builder resources(ResourceConfig resources) {
            ctx.resources = resources;
            return this;
        }

        public Builder images(List<ImageConfiguration> images) {
            ctx.images = images;
            return this;
        }

        public Builder namespace(String namespace) {
            ctx.namespace = namespace;
            return this;
        }

        public Builder useProjectClasspath(boolean useProjectClasspath) {
            ctx.useProjectClasspath = useProjectClasspath;
            return this;
        }

        public Builder openshiftDependencyResources(OpenShiftDependencyResources openShiftDependencyResources) {
            ctx.openshiftDependencyResources = openShiftDependencyResources;
            return this;
        }

        public EnricherContext build() {
            return ctx;
        }

    }
}
