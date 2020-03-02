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
package io.fabric8.maven.core.service.kubernetes.jib;

import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.MojoParameters;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.assembly.AssemblerConfigurationSource;
import org.apache.maven.plugins.assembly.utils.InterpolationConstants;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenReaderFilter;
import org.apache.maven.shared.utils.cli.CommandLineUtils;
import org.codehaus.plexus.interpolation.fixed.FixedStringSearchInterpolator;
import org.codehaus.plexus.interpolation.fixed.PrefixedPropertiesValueSource;
import org.codehaus.plexus.interpolation.fixed.PropertiesBasedValueSource;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public class JibAssemblyConfigurationSource implements AssemblerConfigurationSource {

    private final AssemblyConfiguration assemblyConfig;
    private final MojoParameters params;
    private final JibAssemblyManager.BuildDirs buildDirs;

    // Required by configuration source and duplicated from AbstractAssemblyMojo (which is unfortunately
    // not extracted to be usab;e
    private FixedStringSearchInterpolator commandLinePropertiesInterpolator;
    private FixedStringSearchInterpolator envInterpolator;
    private FixedStringSearchInterpolator rootInterpolator;
    private FixedStringSearchInterpolator mainProjectInterpolator;

    public JibAssemblyConfigurationSource(MojoParameters params, JibAssemblyManager.BuildDirs buildDirs, AssemblyConfiguration assemblyConfig) {
        this.params = params;
        this.assemblyConfig = assemblyConfig;
        this.buildDirs = buildDirs;
    }

    @Override
    public String[] getDescriptors() {
        return Optional.ofNullable(assemblyConfig)
                .map(AssemblyConfiguration::getDescriptor)
                .map(descriptor -> new String[] {EnvUtil.prepareAbsoluteSourceDirPath(params, descriptor).getAbsolutePath() })
                .orElse(new String[0]);
    }

    @Override
    public String[] getDescriptorReferences() {
        return Optional.ofNullable(assemblyConfig)
                .map(AssemblyConfiguration::getDescriptorRef)
                .map(descripterRef -> new String[] {descripterRef})
                .orElse(null);
    }

    // ============================================================================================

    @Override
    public File getOutputDirectory() {
        return buildDirs.getOutputDirectory();
    }

    @Override
    public File getWorkingDirectory() {
        return buildDirs.getWorkingDirectory();
    }

    @Override
    public File getTemporaryRootDirectory() {
        return buildDirs.getTemporaryRootDirectory();
    }

    @Override
    public String getFinalName() {
        return ".";
    }

    @Override
    public ArtifactRepository getLocalRepository() {
        return params.getSession().getLocalRepository();
    }

    public MavenFileFilter getMavenFileFilter() {
        return params.getMavenFileFilter();
    }

    // Maybe use injection
    @Override
    public List<MavenProject> getReactorProjects() {
        return params.getReactorProjects();
    }

    // Maybe use injection
    @Override
    public List<ArtifactRepository> getRemoteRepositories() {
        return params.getProject().getRemoteArtifactRepositories();
    }

    @Override
    public MavenSession getMavenSession() {
        return params.getSession();
    }

    @Override
    public MavenArchiveConfiguration getJarArchiveConfiguration() {
        return params.getArchiveConfiguration();
    }

    @Override
    public String getEncoding() {
        return params.getProject().getProperties().getProperty("project.build.sourceEncoding");
    }

    @Override
    public String getEscapeString() {
        return null;
    }

    @Override
    public List<String> getDelimiters() {
        return null;
    }


    @Nonnull
    public FixedStringSearchInterpolator getCommandLinePropsInterpolator()
    {
        if (commandLinePropertiesInterpolator == null) {
            this.commandLinePropertiesInterpolator = createCommandLinePropertiesInterpolator();
        }
        return commandLinePropertiesInterpolator;
    }

    @Nonnull
    public FixedStringSearchInterpolator getEnvInterpolator()
    {
        if (envInterpolator == null) {
            this.envInterpolator = createEnvInterpolator();
        }
        return envInterpolator;
    }

    @Nonnull public FixedStringSearchInterpolator getRepositoryInterpolator()
    {
        if (rootInterpolator == null) {
            this.rootInterpolator = createRepositoryInterpolator();
        }
        return rootInterpolator;
    }


    @Nonnull
    public FixedStringSearchInterpolator getMainProjectInterpolator()
    {
        if (mainProjectInterpolator == null) {
            this.mainProjectInterpolator = mainProjectInterpolator(getProject());
        }
        return mainProjectInterpolator;
    }

    @Override
    public MavenProject getProject() {
        return params.getProject();
    }

    @Override
    public File getBasedir() {
        return params.getProject().getBasedir();
    }


    @Override
    public boolean isIgnoreDirFormatExtensions() {
        return true;
    }


    @Override
    public boolean isDryRun() {
        return false;
    }


    @Override
    public List<String> getFilters() {
        return Collections.emptyList();
    }

    @Override
    public boolean isIncludeProjectBuildFilters() {
        return true;
    }


    @Override
    public File getDescriptorSourceDirectory() {
        return null;
    }


    @Override
    public File getArchiveBaseDirectory() {
        return null;
    }


    @Override
    public String getTarLongFileMode() {
        return assemblyConfig.getTarLongFileMode() == null ? "warn" : assemblyConfig.getTarLongFileMode();
    }


    @Override
    public File getSiteDirectory() {
        return null;
    }


    @Override
    public boolean isAssemblyIdAppended() {
        return false;
    }


    @Override
    public boolean isIgnoreMissingDescriptor() {
        return false;
    }

    @Override
    public String getArchiverConfig() {
        return null;
    }

    @Override
    public MavenReaderFilter getMavenReaderFilter() {
        return params.getMavenFilterReader();
    }

    @Override
    public boolean isUpdateOnly() {
        return false;
    }

    @Override
    public boolean isUseJvmChmod() {
        return false;
    }

    @Override
    public boolean isIgnorePermissions() {
        return assemblyConfig != null ? assemblyConfig.isIgnorePermissions() : false;
    }

    // =======================================================================
    // Taken from AbstractAssemblyMojo

    private FixedStringSearchInterpolator mainProjectInterpolator(MavenProject mainProject)
    {
        if (mainProject != null) {
            // 5
            return FixedStringSearchInterpolator.create(
                    new org.codehaus.plexus.interpolation.fixed.PrefixedObjectValueSource(
                            InterpolationConstants.PROJECT_PREFIXES, mainProject, true ),

                    // 6
                    new org.codehaus.plexus.interpolation.fixed.PrefixedPropertiesValueSource(
                            InterpolationConstants.PROJECT_PROPERTIES_PREFIXES, mainProject.getProperties(), true ) );
        }
        else {
            return FixedStringSearchInterpolator.empty();
        }
    }

    private FixedStringSearchInterpolator createRepositoryInterpolator()
    {
        final Properties settingsProperties = new Properties();
        final MavenSession session = getMavenSession();

        if (getLocalRepository() != null) {
            settingsProperties.setProperty("localRepository", getLocalRepository().getBasedir());
            settingsProperties.setProperty("settings.localRepository", getLocalRepository().getBasedir());
        }
        else if (session != null && session.getSettings() != null) {
            settingsProperties.setProperty("localRepository", session.getSettings().getLocalRepository() );
            settingsProperties.setProperty("settings.localRepository", getLocalRepository().getBasedir() );
        }
        return FixedStringSearchInterpolator.create(new PropertiesBasedValueSource(settingsProperties));
    }

    private FixedStringSearchInterpolator createCommandLinePropertiesInterpolator()
    {
        Properties commandLineProperties = System.getProperties();
        final MavenSession session = getMavenSession();

        if (session != null) {
            commandLineProperties = new Properties();
            if (session.getSystemProperties() != null) {
                commandLineProperties.putAll(session.getSystemProperties());
            }
            if (session.getUserProperties() != null) {
                commandLineProperties.putAll(session.getUserProperties());
            }
        }
        PropertiesBasedValueSource cliProps = new PropertiesBasedValueSource( commandLineProperties );
        return FixedStringSearchInterpolator.create( cliProps );
    }

    private FixedStringSearchInterpolator createEnvInterpolator() {
        PrefixedPropertiesValueSource envProps = new PrefixedPropertiesValueSource(Collections.singletonList("env."),
                CommandLineUtils.getSystemEnvVars(false), true );
        return FixedStringSearchInterpolator.create( envProps );
    }
}

