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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.model.Configuration;
import io.fabric8.maven.core.model.Dependency;
import io.fabric8.maven.core.model.GroupArtifactVersion;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.core.util.OpenShiftDependencyResources;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.enricher.api.util.MavenConfigurationExtractor;
import io.fabric8.maven.enricher.api.util.ProjectClassLoaders;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
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

    // overall configuration for the build
    private Configuration configuration;

    private MavenProject project;
    private Logger log;

    private OpenShiftDependencyResources openshiftDependencyResources;
    private MavenSession session;

    private MavenEnricherContext() {}

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public Logger getLog() {
        return log;
    }


    @Override
    public OpenShiftDependencyResources getOpenshiftDependencyResources() {
        return openshiftDependencyResources;
    }

    @Override
    public GroupArtifactVersion getGav() {

        return new GroupArtifactVersion(project.getGroupId(),
                                        project.getArtifactId(),
                                        project.getVersion());
    }


    @Override
    public File getProjectDirectory() {
        return getProject().getBasedir();
    }

    @Override
    public String getOutputDirectory() {
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

    @Override
    public List<Dependency> getDependencies(boolean transitive) {
        final Set<Artifact> artifacts = transitive ?
            getProject().getArtifacts() : getProject().getDependencyArtifacts();

        final List<Dependency> dependencies = new ArrayList<>();

        for (Artifact artifact : artifacts) {
            dependencies.add(
                new Dependency(new GroupArtifactVersion(artifact.getGroupId(),
                                                        artifact.getArtifactId(),
                                                        artifact.getVersion()),
                               artifact.getType(),
                               artifact.getScope(),
                               artifact.getFile()));
        }

        return dependencies;
    }

    @Override
    public boolean hasPlugin(String groupId, String artifactId) {
        if (groupId != null) {
            return MavenUtil.hasPlugin(getProject(), groupId, artifactId);
        } else {
            return MavenUtil.hasPluginOfAnyGroupId(getProject(), artifactId);
        }
    }

    @Override
    public ProjectClassLoaders getProjectClassLoaders() {
        return new ProjectClassLoaders(MavenUtil.getCompileClassLoader(getProject()),
                                       MavenUtil.getTestClassLoader(getProject()));
    }


    // ========================================================================
    // Maven specific methods, only available after casting
    public MavenProject getProject() {
        return project;
    }

    public Settings getSettings() {
        return session != null ? session.getSettings() : null;
    }


    private Server getServer(final Settings settings, final String serverId) {
        if (settings == null || StringUtils.isBlank(serverId)) {
            return null;
        }
        return settings.getServer(serverId);
    }


    // =======================================================================================================
    public static class Builder {

        private MavenEnricherContext ctx = new MavenEnricherContext();

        private ResourceConfig resources;
        private List<ImageConfiguration> images;
        private ProcessorConfig processorConfig;

        public Builder session(MavenSession session) {
            ctx.session = session;
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
            this.processorConfig = config;
            return this;
        }

        public Builder resources(ResourceConfig resources) {
            this.resources = resources;
            return this;
        }

        public Builder images(List<ImageConfiguration> images) {
            this.images = images;
            return this;
        }

        public Builder openshiftDependencyResources(OpenShiftDependencyResources openShiftDependencyResources) {
            ctx.openshiftDependencyResources = openShiftDependencyResources;
            return this;
        }

        public MavenEnricherContext build() {
            ctx.configuration =
                new Configuration.Builder()
                    .properties(ctx.project.getProperties())
                    .images(images)
                    .resource(resources)
                    .processorConfig(processorConfig)
                    .pluginConfigLookup(
                                  (system, id) -> {
                                      if (!"maven".equals(system)) {
                                          return Optional.empty();
                                      }
                                      final Plugin plugin = ctx.project.getPlugin(id);
                                      if (plugin == null) {
                                          return Optional.empty();
                                      }
                                      return Optional.of(MavenConfigurationExtractor.extract((Xpp3Dom) plugin.getConfiguration()));
                                  })
                    .build();
            return ctx;
        }

    }
}
