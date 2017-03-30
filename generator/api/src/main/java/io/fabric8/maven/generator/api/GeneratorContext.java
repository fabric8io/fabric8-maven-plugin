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
import io.fabric8.maven.core.service.Fabric8ServiceHub;
import io.fabric8.maven.core.util.GoalFinder;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

/**
 * @author roland
 * @since 15/05/16
 */
public class GeneratorContext {
    private MavenProject project;
    private MavenSession session;
    private GoalFinder goalFinder;
    private ProcessorConfig config;
    private String goalName;
    private Logger logger;
    private PlatformMode mode;
    private OpenShiftBuildStrategy strategy;
    private boolean useProjectClasspath;
    private boolean prePackagePhase;
    private Fabric8ServiceHub fabric8ServiceHub;

    private GeneratorContext() {
    }

    public MavenProject getProject() {
        return project;
    }

    public MavenSession getSession() {
        return session;
    }

    public GoalFinder getGoalFinder() {
        return goalFinder;
    }

    public ProcessorConfig getConfig() {
        return config;
    }

    public String getGoalName() {
        return goalName;
    }

    public Logger getLogger() {
        return logger;
    }

    public PlatformMode getMode() {
        return mode;
    }

    public OpenShiftBuildStrategy getStrategy() {
        return strategy;
    }

    public Fabric8ServiceHub getFabric8ServiceHub() {
        return fabric8ServiceHub;
    }

    /**
     * Returns true if we are in watch mode
     */
    public boolean isWatchMode() throws MojoExecutionException {
        return runningWithGoal("fabric8:watch-spring-boot", "fabric8:watch");
    }

    /**
     * Returns true if maven is running with any of the given goals
     */
    public boolean runningWithGoal(String... goals) throws MojoExecutionException {
        for (String goal : goals) {
            if (goalFinder.runningWithGoal(project, session, goal)) {
                return true;
            }
        }
        return false;
    }

    public boolean isUseProjectClasspath() {
        return useProjectClasspath;
    }

    public boolean isPrePackagePhase() {
        return prePackagePhase;
    }

    // ========================================================================

    public static class Builder {

        private GeneratorContext ctx = new GeneratorContext();

        public Builder config(ProcessorConfig config) {
            ctx.config = config;
            return this;
        }

        public Builder project(MavenProject project) {
            ctx.project = project;
            return this;
        }

        public Builder session(MavenSession session) {
            ctx.session = session;
            return this;
        }

        public Builder goalFinder(GoalFinder goalFinder) {
            ctx.goalFinder = goalFinder;
            return this;
        }

        public Builder goalName(String goalName) {
            ctx.goalName = goalName;
            return this;
        }

        public Builder logger(Logger logger) {
            ctx.logger = logger;
            return this;
        }

        public Builder mode(PlatformMode mode) {
            ctx.mode = mode;
            return this;
        }

        public Builder strategy(OpenShiftBuildStrategy strategy) {
            ctx.strategy = strategy;
            return this;
        }

        public Builder useProjectClasspath(boolean useProjectClasspath) {
            ctx.useProjectClasspath = useProjectClasspath;
            return this;
        }

        public Builder prePackagePhase(boolean prePackagePhase) {
            ctx.prePackagePhase = prePackagePhase;
            return this;
        }

        public Builder fabric8ServiceHub(Fabric8ServiceHub fabric8ServiceHub) {
            ctx.fabric8ServiceHub = fabric8ServiceHub;
            return this;
        }

        public GeneratorContext build() {
            return ctx;
        }
    }
}
