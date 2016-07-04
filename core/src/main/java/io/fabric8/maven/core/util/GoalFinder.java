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

package io.fabric8.maven.core.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.DefaultLifecycles;
import org.apache.maven.lifecycle.Lifecycle;
import org.apache.maven.lifecycle.LifecycleMappingDelegate;
import org.apache.maven.lifecycle.internal.DefaultLifecycleMappingDelegate;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

/**
 * @author roland
 * @since 24/06/16
 */
@Component(role = GoalFinder.class, instantiationStrategy = "per-lookup")
public class GoalFinder {

    // Component used for detecting whether other goals are part of the Maven run.
    @Requirement
    private DefaultLifecycles defaultLifeCycles;

    @Requirement(hint = DefaultLifecycleMappingDelegate.HINT)
    private LifecycleMappingDelegate standardDelegate;

    @Requirement
    private Map<String, LifecycleMappingDelegate> delegates;

    public boolean runningWithGoal(MavenProject project, MavenSession session, String goal) throws MojoExecutionException {
        // We need to check (a) whether it was called explicitely or (b) is part of the lifecycle.
        for (String goalOrPhase : session.getGoals()) {
            // Check if it is a phase
            if (!goalOrPhase.contains(":")) {
                if (checkGoalInPhase(project, session, goal, goalOrPhase)) {
                    return true;
                }
            } else if (goal.equals(goalOrPhase)) {
                // we are called directly
                return true;
            }
        }
        return false;
    }

    private boolean checkGoalInPhase(MavenProject project, MavenSession session, String goal, String phase)
        throws MojoExecutionException {
        Lifecycle lifecycle = defaultLifeCycles.get(phase);
        if (lifecycle == null) {
            throw new MojoExecutionException("Cannot find lifecycle phase " + phase);
        }
        LifecycleMappingDelegate delegate = findDelegate(lifecycle);
        try {
            Map<String, List<MojoExecution>> executionsMap =  delegate.calculateLifecycleMappings(session, project, lifecycle, phase);
            boolean foundPhase = false;
            boolean foundGoal = false;

            for (String p : lifecycle.getPhases()) {
                List<MojoExecution> executions = executionsMap.get(p);
                if (executions != null) {
                    for (MojoExecution execution : executions) {
                        MojoDescriptor desc = execution.getMojoDescriptor();
                        if (desc != null && desc.getFullGoalName().equals(goal)) {
                            foundGoal = true;
                            break;
                        }
                    }
                }
                if (phase.equals(p)) {
                    foundPhase = true;
                    break;
                }
            }
            return foundPhase && foundGoal;
        } catch (Exception e) {
            throw new MojoExecutionException("Interna: Cannot extract executions",e);
        }
    }

    // Find a delegate
    private LifecycleMappingDelegate findDelegate(Lifecycle lifecycle) {
        LifecycleMappingDelegate delegate;
        String lifecycleId = lifecycle.getId();
        if (Arrays.binarySearch(DefaultLifecycles.STANDARD_LIFECYCLES, lifecycleId) >= 0) {
            delegate = standardDelegate;
        } else {
            delegate = delegates.get(lifecycleId);
            if (delegate == null) {
                delegate = standardDelegate;
            }
        }
        return delegate;
    }
}
