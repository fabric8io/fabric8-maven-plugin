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
import io.fabric8.maven.core.util.GoalFinder;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

/**
 * @author roland
 * @since 15/05/16
 */
public class MavenGeneratorContext {
    private final MavenProject project;
    private final MavenSession session;
    private final GoalFinder goalFinder;
    private final ProcessorConfig config;
    private final String goalName;
    private final Logger log;
    private final PlatformMode mode;
    private final OpenShiftBuildStrategy strategy;

    public MavenGeneratorContext(MavenProject project, MavenSession session, GoalFinder goalFinder, ProcessorConfig generatorConfig, String goalName, Logger log,
                                 PlatformMode mode, OpenShiftBuildStrategy strategy) {
        this.project = project;
        this.session = session;
        this.goalFinder = goalFinder;
        this.config = generatorConfig;
        this.goalName = goalName;
        this.log = log;
        this.mode = mode;
        this.strategy = strategy;
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

    public Logger getLog() {
        return log;
    }

    public PlatformMode getMode() {
        return mode;
    }

    public OpenShiftBuildStrategy getStrategy() {
        return strategy;
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
            if (goalFinder.runningWithGoal(project, session,  goal)) {
                return true;
            }
        }
        return false;
    }
}
