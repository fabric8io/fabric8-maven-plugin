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

import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.util.GoalFinder;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.core.util.OpenShiftDependencyResources;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.enricher.api.util.MavenConfigurationExtractor;
import io.fabric8.maven.enricher.api.util.ProjectClassLoader;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Site;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * The context given to each enricher from where it can extract build specific information.
 *
 * @author roland
 * @since 01/04/16
 */
public class MavenEnricherContext implements EnricherContext {

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

    private MavenEnricherContext() {}

    public MavenProject getProject() {
        return project;
    }

    @Override
    public List<ImageConfiguration> getImages() {
        return images;
    }

    public Logger getLog() {
        return log;
    }

    @Override
    public ProcessorConfig getConfig() {
        return config;
    }

    @Override
    public ResourceConfig getResources() {
        return resources;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public boolean isUseProjectClasspath() {
        return useProjectClasspath;
    }

    public Settings getSettings() {
        return session != null ? session.getSettings() : null;
    }

    @Override
    public OpenShiftDependencyResources getOpenshiftDependencyResources() {
        return openshiftDependencyResources;
    }

    @Override
    public boolean isWatchMode() {
        return runningWithGoal("fabric8:watch-spring-boot", "fabric8:watch");
    }

    @Override
    public boolean runningWithGoal(String... goals) {
        for (String goal : goals) {
            try {
                if (goalFinder.runningWithGoal(project, session,  goal)) {
                    return true;
                }
            } catch (MojoExecutionException e) {
                throw new IllegalStateException("Cannot determine maven goals", e);
            }
        }
        return false;
    }

    @Override
    public Properties getProperties() {
        final MavenProject currentProject = getProject();
        if (currentProject == null) {
            return new Properties();
        }
        return currentProject.getProperties();
    }

    @Override
    public Map<String, Object> getConfiguration(String id) {
        final Plugin plugin = getProject().getPlugin(id);

        if (plugin == null) {
            return new HashMap<>();
        }

        return MavenConfigurationExtractor.extract((Xpp3Dom) plugin.getConfiguration());
    }

    @Override
    public io.fabric8.maven.core.model.Artifact getArtifact() {

        return new io.fabric8.maven.core.model.Artifact(getProject().getGroupId(),getProject().getArtifactId(), getProject().getVersion());
    }

    @Override
    public String getRootArtifactId() {
        return MavenUtil.getRootProject(getProject()).getArtifactId();
    }

    @Override
    public File getRootDir() {
        return MavenUtil.getRootProject(getProject()).getBasedir();
    }

    @Override
    public File getCurrentDir() {
        return getProject().getBasedir();
    }

    @Override
    public String getBuildOutputDirectory() {
        return getProject().getBuild().getOutputDirectory();
    }

    @Override
    public DockerRegistryAuthentication getDockerRegistryAuth(String serverId) {
        Server server = getServer(getSettings(), serverId);

        if (server == null) {
            return null;
        }

        final Map<String, Object> conf = MavenConfigurationExtractor.extract((Xpp3Dom) server.getConfiguration());

        String mail = (String) conf.get("email");

        return new DockerRegistryAuthentication(server.getUsername(), server.getPassword(), mail);
    }

    private Server getServer(final Settings settings, final String serverId) {
        if (settings == null || StringUtils.isBlank(serverId)) {
            return null;
        }
        return settings.getServer(serverId);
    }

    @Override
    public boolean isClassInCompileClasspath(boolean all, String... clazz) {
        if (all) {
            return  MavenUtil.hasAllClasses(getProject(), clazz);
        } else {
            return MavenUtil.hasClass(getProject(), clazz);
        }
    }

    @Override
    public String getDocumentationUrl() {
        DistributionManagement distributionManagement = findProjectDistributionManagement();
        if (distributionManagement != null) {
            Site site = distributionManagement.getSite();
            if (site != null) {
                return site.getUrl();
            }
        }
        return null;
    }

    private DistributionManagement findProjectDistributionManagement() {
        MavenProject currentProject = getProject();
        while (currentProject != null) {
            DistributionManagement distributionManagement = currentProject.getDistributionManagement();
            if (distributionManagement != null) {
                return distributionManagement;
            }
            currentProject = currentProject.getParent();
        }
        return null;
    }

    @Override
    public List<Dependency> getDependencies(boolean transitive) {
        final Set<Artifact> artifacts = transitive ?
            getProject().getArtifacts() : getProject().getDependencyArtifacts();

        final List<Dependency> dependencies = new ArrayList<>();

        for (Artifact artifact : artifacts) {
            dependencies.add(new Dependency(artifact.getType(), artifact.getScope(), artifact.getFile()));
        }

        return dependencies;
    }

    @Override
    public boolean hasDependency(String groupId, String artifactId) {
        return MavenUtil.hasDependency(getProject(), groupId, artifactId);
    }

    @Override
    public boolean hasPlugin(String plugin) {
        return MavenUtil.hasPlugin(getProject(), plugin);
    }

    @Override
    public boolean hasDependencyOnAnyArtifactOfGroup(String groupId) {
        return MavenUtil.hasDependencyOnAnyArtifactOfGroup(getProject(), groupId);
    }

    @Override
    public boolean hasPluginOfAnyGroupId(String groupId) {
        return MavenUtil.hasPluginOfAnyGroupId(getProject(), groupId);
    }

    @Override
    public String getDependencyVersion(String groupId, String artifactId) {
        return MavenUtil.getDependencyVersion(getProject(), groupId, artifactId);
    }

    @Override
    public ProjectClassLoader getProjectClassLoader() {
        return new ProjectClassLoader(MavenUtil.getCompileClassLoader(getProject()), MavenUtil.getTestClassLoader(getProject()));
    }

    @Override
    public List<String> getCompileClasspathElements() {
        try {
            return getProject().getCompileClasspathElements();
        } catch (DependencyResolutionRequiredException e) {
            log.warn("Instructed to use project classpath, but cannot. Continuing build if we can: ", e);
        }
        return new ArrayList<>();
    }

    // =======================================================================================================
    public static class Builder {

        private MavenEnricherContext ctx = new MavenEnricherContext();

        public Builder session(MavenSession session) {
            ctx.session = session;
            return this;
        }

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

        public MavenEnricherContext build() {
            return ctx;
        }

    }
}
