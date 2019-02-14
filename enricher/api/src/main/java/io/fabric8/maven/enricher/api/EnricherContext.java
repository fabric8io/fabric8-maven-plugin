/**
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

import java.io.File;
import java.util.List;
import java.util.Optional;

import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.config.RuntimeMode;
import io.fabric8.maven.core.model.Configuration;
import io.fabric8.maven.core.model.Dependency;
import io.fabric8.maven.core.model.GroupArtifactVersion;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.enricher.api.util.ProjectClassLoaders;

public interface EnricherContext {

    /**
     * Get the current artifact with its parameters
     *
     * @return the artifact
     */
    GroupArtifactVersion getGav();

    /**
     * Get Logger.
     * @return Logger.
     */
    Logger getLog();

    /**
     * The configuration specific to the enricher.
     *
     * @return configuration to use
     */
    Configuration getConfiguration();

    /**
     * Base directory of the project. E.g. for Maven that's the directory
     * where the pom.xml is placed in
     * @return the projects based directory
     */
    File getProjectDirectory();

    /**
     * Get various class loaders used in the projects
     *
     * @return compile and test class loader
     */
    ProjectClassLoaders getProjectClassLoaders();

    /**
     * Check if a given plugin is present
     *
     * @param groupId group id of plugin to check. If null any group will be considered.
     * @param artifactId of plugin to check
     * @return true if a plugin exists, false otherwise.
     */
    boolean hasPlugin(String groupId, String artifactId);

    /**
     * Gets dependencies defined in build tool
     * @param transitive if transitive deps should be returned.
     * @return List of dependencies.
     */
    List<Dependency> getDependencies(boolean transitive);

    /**
     * Checks if given dependency is defined.
     * @param groupId of dependency.
     * @param artifactId of dependency. If null, check if there is any dependency with the given group
     * @return True if present, false otherwise.
     */
    default boolean hasDependency(String groupId, String artifactId) {
        return getDependencyVersion(groupId, artifactId).isPresent();
    }

    /**
     * Gets version of given dependency.
     * @param groupId of the dependency.
     * @param artifactId of the dependency.
     * @return Version number.
     */
    default Optional<String> getDependencyVersion(String groupId, String artifactId) {
        List<Dependency> dependencies = getDependencies(true);
        for (Dependency dep : dependencies) {
            String scope = dep.getScope();
            if ("test".equals(scope) ||
                (artifactId != null && !artifactId.equals(dep.getGav().getArtifactId()))) {
                continue;
            }
            if (dep.getGav().getGroupId().equals(groupId)) {
                return Optional.of(dep.getGav().getVersion());
            }
        }
        return Optional.empty();
    }


    PlatformMode getPlatformMode();

    RuntimeMode getRuntimeMode();
}
