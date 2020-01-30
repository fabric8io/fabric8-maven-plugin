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
package io.fabric8.maven.core.util;

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.LayerConfiguration;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.AssemblyMode;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.assembly.AssemblerConfigurationSource;
import org.apache.maven.plugins.assembly.InvalidAssemblerConfigurationException;
import org.apache.maven.plugins.assembly.archive.ArchiveCreationException;
import org.apache.maven.plugins.assembly.archive.AssemblyArchiver;
import org.apache.maven.plugins.assembly.format.AssemblyFormattingException;
import org.apache.maven.plugins.assembly.io.AssemblyReadException;
import org.apache.maven.plugins.assembly.io.AssemblyReader;
import org.apache.maven.plugins.assembly.model.Assembly;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.AbstractUnArchiver;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.archiver.tar.TarUnArchiver;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.apache.commons.io.FileUtils;
import org.codehaus.plexus.logging.AbstractLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

@Component(role = JibAssemblyManager.class, instantiationStrategy = "per-lookup")
public class JibAssemblyManager {

    @Requirement
    private AssemblyReader assemblyReader;

    @Requirement
    private AssemblyArchiver assemblyArchiver;

    public Assembly getAssemblyConfig(AssemblyConfiguration assemblyConfiguration, JibAssemblyConfigurationSource source)
            throws MojoExecutionException {
        Assembly assembly = assemblyConfiguration.getInline();
        if (assembly == null) {
            assembly = extractAssembly(source);
        }
        return assembly;
    }

    private Assembly extractAssembly(AssemblerConfigurationSource config) throws MojoExecutionException {
        try {
            List<Assembly> assemblies = assemblyReader.readAssemblies(config);
            if (assemblies.size() != 1) {
                throw new MojoExecutionException("Only one assembly can be used for creating a base image (and not "
                        + assemblies.size() + ")");
            }
            return assemblies.get(0);
        }
        catch (AssemblyReadException e) {
            throw new MojoExecutionException("Error reading assembly: " + e.getMessage(), e);
        }
        catch (InvalidAssemblerConfigurationException e) {
            throw new MojoExecutionException(assemblyReader, e.getMessage(), "Assembly configuration is invalid: " + e.getMessage());
        }
    }

    public void createAssemblyArchive(AssemblyConfiguration assemblyConfiguration, JibAssemblyConfigurationSource source, MojoParameters params) throws MojoExecutionException {
        Assembly assembly = getAssemblyConfig(assemblyConfiguration, source);

        AssemblyMode buildMode = assemblyConfiguration.getMode();
        File originalArtifactFile = null;
        try {
            originalArtifactFile = ensureThatArtifactFileIsSet(params.getProject());
            assembly.setId("jib");
            assemblyArchiver.createArchive(assembly, assemblyConfiguration.getName(), buildMode.getExtension(), source, false, null);
        } catch (ArchiveCreationException | AssemblyFormattingException e) {
            String error = "Failed to create assembly archive for jib container image " +
                    " (with mode '" + buildMode + "'): " + e.getMessage() + ".";
            if (params.getProject().getArtifact().getFile() == null) {
                error += " If you include the build artifact please ensure that you have " +
                        "built the artifact before with 'mvn package' (should be available in the target/ dir). " +
                        "Please see the documentation (section \"Assembly\") for more information.";
            }
            throw new MojoExecutionException(error, e);
        } catch (InvalidAssemblerConfigurationException e) {
            throw new MojoExecutionException(assembly, "Assembly is incorrectly configured: " + assembly.getId(),
                    "Assembly: " + assembly.getId() + " is not configured correctly: "
                            + e.getMessage());
        } finally {
            setArtifactFile(params.getProject(), originalArtifactFile);
        }
    }

    // Set an artifact file if it is missing. This workaround the issues
    // mentioned first in https://issues.apache.org/jira/browse/MASSEMBLY-94 which requires the package
    // phase to run so set the ArtifactFile. There is no good solution, so we are trying
    // to be very defensive and add a workaround for some situation which won't work for every occasion.
    // Unfortunately a plain forking of the Maven lifecycle is not good enough, since the MavenProject
    // gets cloned before the fork, and the 'package' plugin (e.g. JarPlugin) sets the file on the cloned
    // object which is then not available for the BuildMojo (there the file is still null leading to the
    // the "Cannot include project artifact: ... The following patterns were never triggered in this artifact inclusion filter: <artifact>"
    // warning with an error following.
    private File ensureThatArtifactFileIsSet(MavenProject project) {
        Artifact artifact = project.getArtifact();
        if (artifact == null) {
            return null;
        }
        File oldFile = artifact.getFile();
        if (oldFile != null) {
            return oldFile;
        }
        Build build = project.getBuild();
        if (build == null) {
            return null;
        }
        String finalName = build.getFinalName();
        String target = build.getDirectory();
        if (finalName == null || target == null) {
            return null;
        }
        File artifactFile = new File(target, finalName + "." + project.getPackaging());
        if (artifactFile.exists() && artifactFile.isFile()) {
            setArtifactFile(project, artifactFile);
        }
        return null;
    }

    private void setArtifactFile(MavenProject project, File artifactFile) {
        Artifact artifact = project.getArtifact();
        if (artifact != null) {
            artifact.setFile(artifactFile);
        }
    }


    public File extractOrCopy(AssemblyMode mode, File source, File destinationDir, String assemblyName, Logger log) throws IOException {

        if (source.isDirectory() && mode.getExtension().equals("dir")) {

            FileUtils.copyDirectoryToDirectory(source, destinationDir);
            return destinationDir;

        } else {

            File destination = new File(destinationDir, assemblyName);

            if (!destination.exists()) {
                destination.mkdir();
            }

            AbstractUnArchiver unArchiver = null;

            switch (mode.getExtension()) {
                case "zip" :
                    unArchiver = new ZipUnArchiver();
                    break;
                case "tar" :
                    unArchiver = new TarUnArchiver();
                    break;
                case "tgz" :
                    unArchiver = new TarGZipUnArchiver();
            }

            if (unArchiver != null) {
                unArchiver.setSourceFile(source);
                unArchiver.setDestDirectory(destinationDir);
                unArchiver.enableLogging(getLogger(log));
                unArchiver.extract();

                return destination;
            }

            return null;
        }
    }

    public void makeAllFilesExecutable(File directory) throws IOException {
        Files.walkFileTree(directory.toPath(), new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                file.toFile().setExecutable(true, false);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                if (exc != null) {
                    throw new IOException(exc);
                }
                return FileVisitResult.TERMINATE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw new IOException(exc);
                }
                return FileVisitResult.CONTINUE;
            }
        });

    }

    public void copyToContainer(JibContainerBuilder containerBuilder, File directory, String targetDir) throws IOException {
        Files.walkFileTree(directory.toPath(), new FileVisitor<Path>() {
            boolean notParentDir = false;
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {

                if (!notParentDir) {
                    notParentDir = true;
                    return FileVisitResult.CONTINUE;
                }

                containerBuilder.addLayer(LayerConfiguration.builder()
                        .addEntryRecursive(dir, AbsoluteUnixPath.fromPath(Paths.get(targetDir, dir.getFileName().toString())))
                        .build());
                return FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                containerBuilder.addLayer(LayerConfiguration.builder()
                        .addEntry(file, AbsoluteUnixPath.fromPath(Paths.get(targetDir, file.getFileName().toString())))
                        .build());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                if (exc != null) {
                    throw new IOException(exc);
                }
                return FileVisitResult.TERMINATE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw new IOException(exc);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static org.codehaus.plexus.logging.Logger getLogger(Logger log) {
        return new AbstractLogger(1, "fmp-logger") {
            @Override
            public void debug(String message, Throwable throwable) {
                log.debug(message, throwable);
            }

            @Override
            public void info(String message, Throwable throwable) {
                log.info(message, throwable);
            }

            @Override
            public void warn(String message, Throwable throwable) {
                log.warn(message, throwable);
            }

            @Override
            public void error(String message, Throwable throwable) {
                log.error(message, throwable);
            }

            @Override
            public void fatalError(String message, Throwable throwable) {
                log.error(message, throwable);
            }

            @Override
            public org.codehaus.plexus.logging.Logger getChildLogger(String name) {
                return null;
            }
        };
    }

    static class BuildDirs {

        private final String buildTopDir;
        private final MojoParameters params;

        /**
         * Constructor building up the the output directories
         *
         * @param imageName image name for the image to build
         * @param params mojo params holding base and global outptput dir
         */
        BuildDirs(String imageName, MojoParameters params) {
            this.params = params;
            // Replace tag separator with a slash to avoid problems
            // with OSs which gets confused by colons.
            this.buildTopDir = imageName != null ? imageName.replace(':', '/') : null;
        }

        File getOutputDirectory() {
            return getDir("build");
        }

        File getWorkingDirectory() {
            return getDir("work");
        }

        File getTemporaryRootDirectory() {
            return getDir("tmp");
        }

        void createDirs() {
            for (String workDir : new String[] { "build", "work", "tmp" }) {
                File dir = getDir(workDir);
                if (!dir.exists()) {
                    if(!dir.mkdirs()) {
                        throw new IllegalArgumentException("Cannot create directory " + dir.getAbsolutePath());
                    }
                }
            }
        }

        private File getDir(String dir) {
            return EnvUtil.prepareAbsoluteOutputDirPath(params, buildTopDir, dir);
        }
    }
}