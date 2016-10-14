/*
 * Copyright 2005-2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.maven.plugin.mojo.infra;

import io.fabric8.maven.core.util.IoUtil;
import io.fabric8.maven.core.util.ProcessUtil;
import io.fabric8.maven.plugin.mojo.AbstractFabric8Mojo;
import io.fabric8.utils.IOHelpers;
import io.fabric8.utils.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;

/**
 * Base class for install/tool related mojos
 */
public abstract class AbstractInstallMojo extends AbstractFabric8Mojo {

    // Command to call for gofabric8
    protected static final String GOFABRIC8 = "gofabric8";

    // Download parameters
    private static final String GOFABRIC8_VERSION_URL = "https://raw.githubusercontent.com/fabric8io/gofabric8/master/version/VERSION";
    private String GOFABRIC_DOWNLOAD_URL_FORMAT = "https://github.com/fabric8io/gofabric8/releases/download/v%s/gofabric8-%s-%s"; // version, platform, arch

    private enum Platform { linux, darwin, windows }
    private enum Architecture { amd64, arm }

    /**
     * Defines the kind of cluster such as `minishift`
     */
    @Parameter(property = "fabric8.cluster.kind")
    protected String clusterKind;

    @Parameter(property = "fabric8.dir", defaultValue = "${user.home}/.fabric8/bin")
    private File fabric8BinDir;

    // X-TODO: Add update semantics similar to setup
    // X-TODO: Maybe combine fabric8:setup and fabric8:install
    // X-TODO: wonder if it should be renamed to fabric8:cluster-install?

    @Component
    private Prompter prompter;

    protected File installBinaries() throws MojoExecutionException {
        File gofabric8 = ProcessUtil.findExecutable(log, GOFABRIC8);
        if (gofabric8 == null) {
            validateFabric8Dir();

            gofabric8 = new File(fabric8BinDir, GOFABRIC8);
            if (!gofabric8.exists() || !gofabric8.isFile() || !gofabric8.canExecute()) {
                // X-TODO: Maybe allow for an update of gofabric8 itself ?
                downloadGoFabric8(gofabric8);
            }

            // --- ✂ --- ✂ --- ✂ --- ✂ --- ✂ --- ✂ --- ✂ --- ✂ --- ✂ --- ✂ --- ✂ --- ✂ --- ✂ --- ✂ --- ✂

            // lets check if the binary directory is on the path
            if (!ProcessUtil.folderIsOnPath(log, fabric8BinDir)) {
                String absolutePath = fabric8BinDir.getAbsolutePath();
                String commandIndent = "  ";
                log.warn("Note that the fabric8 folder " + absolutePath + " is not on the PATH!");
                if (getPlatform().equals(Platform.windows.name())) {
                    log.warn("Please add the following to PATH environment variable:");
                    log.warn(commandIndent + "set PATH=%PATH%;" + absolutePath);
                } else {
                    String bashrcLine = "export PATH=$PATH:" + absolutePath;

                    log.warn("Please add the following to your ~/.bashrc:");
                    log.warn(commandIndent + bashrcLine);

                    File homeDir = getUserHome();
                    File rcFile = null;
                    String[] rcFiles = {".bashrc", ".zshrc", ".profile", ".bash_profile"};
                    for (String fileName : rcFiles) {
                        File testFile = new File(homeDir, fileName);
                        if (fileExists(testFile)) {
                            rcFile = testFile;
                            break;
                        }
                    }
                    if (rcFile == null) {
                        rcFile = new File(".bashrc");
                    }
                    if (prompter != null && rcFile.getParentFile().isDirectory()) {
                        String answer = null;
                        try {
                            answer = prompter.prompt("Would you like to add this line to your ~/" + rcFile.getName() + " now? (Y/n)");
                        } catch (PrompterException e) {
                            log.warn("Failed to ask user prompt: " + e, e);
                        }
                        if (answer != null && answer.startsWith("Y")) {
                            addToBashRC(rcFile, bashrcLine);
                            log.info("Updated " + rcFile + ". Please type the following command to update your current shell:");
                            log.info(commandIndent + "source ~/" + rcFile.getName());
                        }
                    }
                }
            }
        } else {
            getLog().info("Found gofabric8 at: " + gofabric8);
            runGofabric8(gofabric8, "version");
        }
        return gofabric8;
    }

    protected static boolean fileExists(File testFile) {
        return testFile.exists() && testFile.isFile();
    }

    protected void addToBashRC(File bashrcFile, String text) throws MojoExecutionException {
        try (FileWriter writer = new FileWriter(bashrcFile, true)) {
            writer.append("\n# added by fabric8-maven-plugin at " + new Date() + "\n" + text + "\n");
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to append to " + bashrcFile + ". " + e, e);
        }
    }

    protected File getUserHome() {
        return new File(System.getProperty("user.home", "."));
    }

    // Check for a valide ~/.fabric8/bin
    private void validateFabric8Dir() throws MojoExecutionException {
        if (!fabric8BinDir.exists()) {
            if (!fabric8BinDir.mkdirs()) {
                throw new MojoExecutionException(String.format("Failed to create directory %s. Do you have permission on this folder?", fabric8BinDir));
            }
        } else if (!fabric8BinDir.isDirectory()) {
            throw new MojoExecutionException(String.format("%s exists but is not a directory", fabric8BinDir));
        }
    }

    // Download gofabric8
    protected void downloadGoFabric8(File destFile) throws MojoExecutionException {

        // Download to a temporary file
        File tempFile = downloadToTempFile();

        // Move into it's destination place in ~/.fabric8/bin
        moveFile(tempFile, destFile);

        // Make some noise
        runGofabric8(destFile, "version");
    }

    // First download in a temporary place
    private File downloadToTempFile() throws MojoExecutionException {
        // TODO: Very checksum and potentially signature
        File destFile = createGofabric8DownloadFile();
        URL downloadUrl = getGofabric8DownloadUrl();
        IoUtil.download(log, downloadUrl, destFile);
        return destFile;
    }


    // Where to put the initial download of gofabric8
    private File createGofabric8DownloadFile() throws MojoExecutionException {
        File file = null;
        try {
            File downloadDir = Files.createTempDirectory(fabric8BinDir.toPath(), "download").toFile();
            downloadDir.deleteOnExit();
            File ret = new File(downloadDir, "gofabric8");
            ret.deleteOnExit();
            log.debug("Downloading gofabric8 to temporary file %s", ret);
            return ret;
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create a temporary file for the download");
        }
    }

    // Create download URL + log
    private URL getGofabric8DownloadUrl() throws MojoExecutionException {
        String version = getGoFabric8Version();

        String platform = getPlatform().name();
        String arch = getArchitecture().name();
        String releaseUrl = String.format(GOFABRIC_DOWNLOAD_URL_FORMAT, version, platform, arch);
        if (platform.equalsIgnoreCase("windows")) {
            releaseUrl += ".exe";
        }
        log.info("Downloading gofabric8:");
        log.info("   Version:      [[B]]%s[[B]]", version);
        log.info("   Platform:     [[B]]%s[[B]]", platform);
        log.info("   Architecture: [[B]]%s[[B]]", arch);

        try {
            return new URL(releaseUrl);
        } catch (MalformedURLException e) {
            throw new MojoExecutionException("Failed to create URL from " + releaseUrl + ": " + e, e);
        }
    }

    // Move gofabric8 to its final place
    private void moveFile(File tempFile, File destFile) throws MojoExecutionException {
        if (!tempFile.renameTo(destFile)) {
            // lets try copy it instead as this could be an odd linux issue with renaming files
            try {
                IOHelpers.copy(new FileInputStream(tempFile), new FileOutputStream(destFile));
                log.info("Downloaded gofabric8 to %s",destFile);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to copy temporary file " + tempFile + " to " + destFile + ": " + e, e);
            }
        }
        if (!destFile.setExecutable(true)) {
            throw new MojoExecutionException("Cannot make " + destFile + " executable");
        }
    }

    // Download version for gofabric8
    private String getGoFabric8Version() throws MojoExecutionException {
        try {
            String version = IOHelpers.readFully(new URL(GOFABRIC8_VERSION_URL));
            return version;
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to load gofabric8 version from " + GOFABRIC8_VERSION_URL + ". " + e, e);
        }
    }

    private Architecture getArchitecture() {
        String osArch = System.getProperty("os.arch");
        if (osArch != null && osArch.toLowerCase().contains("arm")) {
            return Architecture.arm;
        } else {
            return Architecture.amd64;
        }
    }

    protected Platform getPlatform() {
        String osName = System.getProperty("os.name");
        if (osName.contains("OS X") || osName.contains("Mac ")) {
            return Platform.darwin;
        } else if (osName.contains("Windows")) {
            return Platform.windows;
        } else {
            return Platform.linux;
        }
    }

    protected void runGofabric8(File command, String ... args) throws MojoExecutionException {
        // Be sure to run in batch mode
        List argList = new ArrayList(Arrays.asList(args));
        argList.add("--batch");
        String argLine = Strings.join(argList, " ");
        log.info("Running %s %s", command, argLine);

        String message = command.getName() + argLine;
        try {
            int result = ProcessUtil.runCommand(createExternalProcessLogger("[[B]]gofabric8[[B]] "), command, argList);
            if (result != 0) {
                throw new MojoExecutionException("Failed to execute " + message + " result was: " + result);
            }
        } catch (IOException e) {
            throw new MojoExecutionException(String.format("Failed to execute %s : %s", command, e.getMessage()), e);
        }
    }

    protected boolean isMinishift() {
        if (Strings.isNotBlank(clusterKind)) {
            String text = clusterKind.toLowerCase().trim();
            return text.equals("minishift") || text.equals("openshift");
        }
        return false;
    }
}
