package io.fabric8.maven.enricher.api;

import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.model.Artifact;
import io.fabric8.maven.core.util.OpenShiftDependencyResources;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.enricher.api.util.ProjectClassLoader;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public interface EnricherContext {

    String getNamespace();

    ResourceConfig getResources();

    /**
     * Returns true if we are in watch mode
     */
    boolean isWatchMode();

    OpenShiftDependencyResources getOpenshiftDependencyResources();

    /**
     * Returns true if maven is running with any of the given goals
     */
    boolean runningWithGoal(String... goals);

    /**
     * Get properties of current project. Usually in case of Maven, they are the project properties.
     * @return Properties of project.
     */
    Properties getProperties();

    /**
     * Gets configuration values. Since there can be inner values, it returns a Map of Objects where an Object can be a simple type, List or another Map.
     * @param id where to pick configuration. In case of Maven, plugin id.
     * @return Configuration value.
     */
    Map<String, Object> getConfiguration(String id);

    /**
     * Gets artifact.
     * @return Artifact.
     */
    Artifact getArtifact();

    /**
     * Gets artifact identifier of root project.
     * @return Root artifact id.
     */
    String getRootArtifactId();

    /**
     * Returns the rot dir of project. Notice that in a submodule project, current dir is not the roor dir.
     * @return Root dir.
     */
    File getRootDir();

    /**
     * Gets current directory.
     * @return Current directory.
     */
    File getCurrentDir();

    /**
     * Gets output directory.
     * @return Output Directory.
     */
    String getBuildOutputDirectory();

    /**
     * Gets a map with fields username, password and email set.
     * @param serverId Identifier to get the info.
     * @return Docker Registry authentication parameters.
     */
    DockerRegistryAuthentication getDockerRegistryAuth(String serverId);

    /**
     * Returns if class is in compile classpath.
     * @param all True if all of them must be there.
     * @param clazz fully qualified class name.
     * @return True if present, false otherwise.
     */
    boolean isClassInCompileClasspath(boolean all, String... clazz);

    /**
     * Gets documentation url or null.
     * @return Gets documentation url or null if not specified.
     */
    String getDocumentationUrl();

    /**
     * Gets dependencies defined in build tool
     * @param transitive if transitive deps should be returned.
     * @return List of dependencies.
     */
    List<Dependency> getDependencies(boolean transitive);

    /**
     * Checks if given dependency is defined.
     * @param groupId of dependency.
     * @param artifactId of dependency.
     * @return True if present, flse otherwise.
     */
    boolean hasDependency(String groupId, String artifactId);

    /**
     * Returns if given plugin is present
     * @param plugin to check.
     * @return True if present, false otherwise.
     */
    boolean hasPlugin(String plugin);

    /**
     * Checks if there is a dependency of given group id.
     * @param groupId to search.
     * @return True if there is a dependency, false otherwise.
     */
    boolean hasDependencyOnAnyArtifactOfGroup(String groupId);

    /**
     * Checks if there is a plugin of given group id.
     * @param groupId to search.
     * @return True if there is a plugin, false otherwise.
     */
    boolean hasPluginOfAnyGroupId(String groupId);

    /**
     * Gets version of given dependency.
     * @param groupId of the dependency.
     * @param artifactId of the dependency.
     * @return Version number.
     */
    String getDependencyVersion(String groupId, String artifactId);

    /**
     * Gets Project Classloader.
     * @return Classloader.
     */
    ProjectClassLoader getProjectClassLoader();

    /**
     *
     * Gets processor Config
     * @return processor config
     */
    ProcessorConfig getConfig();

    /**
     * Get Logger.
     * @return Logger.
     */
    Logger getLog();

    /**
     * Get List of images
     * @return
     */
    List<ImageConfiguration> getImages();

    boolean isUseProjectClasspath();

    List<String> getCompileClasspathElements();

}
