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
package io.fabric8.maven.plugin.mojo.infra;

import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.util.IoUtil;
import io.fabric8.maven.core.util.ProcessUtil;
import io.fabric8.maven.plugin.mojo.AbstractFabric8Mojo;
import io.fabric8.utils.IOHelpers;
import io.fabric8.utils.Strings;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Base class for install/tool related mojos
 */
public abstract class AbstractInstallMojo extends AbstractFabric8Mojo {

    // Command to call for gofabric8
    protected static final String GOFABRIC8 = "gofabric8";
    protected static final String KOMPOSE = "kompose";

    // Download parameters
    private static final String GOFABRIC8_VERSION_URL = "https://raw.githubusercontent.com/fabric8io/gofabric8/master/version/VERSION";
    private static final String KOMPOSE_VERSION_URL = "https://raw.githubusercontent.com/kubernetes/kompose/master/build/VERSION";
    public static final String VERSION_ARGUMENT = "version";
    public static final String BATCH_ARGUMENT = "--batch";
    private static String GOFABRIC_DOWNLOAD_URL_FORMAT = "https://github.com/fabric8io/gofabric8/releases/download/v%s/gofabric8-%s-%s"; // version, platform, arch
    private static String KOMPOSE_DOWNLOAD_URL_FORMAT = "https://github.com/kubernetes/kompose/releases/download/v%s/kompose-%s-%s"; // version, platform, arch

    // Variations of gofabric8
    private enum Platform { linux, darwin, windows }
    private enum Architecture { amd64, arm }

    /**
     * Defines the kind of cluster such as `minishift`
     */
    @Parameter(property = "fabric8.cluster.kind")
    protected String clusterKind;

    /**
     * Alternatively, the platform mode is evaluated to detect the kind of cluster
     * to use. 'clusterKind' takes precedence
     */
    @Parameter(property = "fabric8.mode")
    protected PlatformMode mode;

    @Parameter(property = "fabric8.dir", defaultValue = "${user.home}/.fabric8/bin")
    private File fabric8BinDir;

    @Parameter(property = "kompose.dir", defaultValue = "${user.home}/.kompose/bin")
    private File komposeBinDir;

    // X-TODO: Add update semantics similar to setup
    // X-TODO: Maybe combine fabric8:setup and fabric8:install
    // X-TODO: wonder if it should be renamed to fabric8:cluster-install?

    @Component
    private Prompter prompter;

    @Parameter(property = "fabric8.install.batch.mode", defaultValue = "false")
    private boolean installBatchMode;

    /**
     * Check for gofabric8 and install it to ~/.fabric8/bin if not available on the path
     *
     * @return the path to gofabric8
     * @throws MojoExecutionException
     */
    protected File installGofabric8IfNotAvailable() throws MojoExecutionException {
        File gofabric8 = ProcessUtil.findExecutable(log, GOFABRIC8);
        if (gofabric8 == null) {
            gofabric8 = installAndConfigureBinary(fabric8BinDir, GOFABRIC8, GOFABRIC8_VERSION_URL, GOFABRIC_DOWNLOAD_URL_FORMAT);
        } else {
            log.info("Found %s", gofabric8);
        }
        executeGoFabric8Command(gofabric8, VERSION_ARGUMENT);
        return gofabric8;
    }

    /**
     * Check for kompose and install it to ~/.kompose/bin if not available on the path
     *
     * @return the path to kompose file
     * @throws MojoExecutionException
     */
    protected File installKomposeIfNotAvailable() throws MojoExecutionException {
        File kompose = ProcessUtil.findExecutable(log, KOMPOSE);
        if (kompose == null) {
            kompose = installAndConfigureBinary(komposeBinDir, KOMPOSE, KOMPOSE_VERSION_URL, KOMPOSE_DOWNLOAD_URL_FORMAT);
        } else {
            log.info("Found %s", kompose);
        }
        executeCommand(kompose, KOMPOSE, VERSION_ARGUMENT);
        return kompose;
    }

    private File installAndConfigureBinary(File binDirectory, String binName, String binVersionUrl, String binDownloadUrlFormat) throws MojoExecutionException {
        File binaryFile = null;

        validateDir(binDirectory);

        String fileName = binName;
        if (Platform.windows.equals(getPlatform())) {
            fileName += ".exe";
        }
        binaryFile = new File(binDirectory, fileName);
        if (!binaryFile.exists() || !binaryFile.isFile() || !binaryFile.canExecute()) {
            downloadExecutable(binVersionUrl, binDownloadUrlFormat, binDirectory, binaryFile, binName);
        }

        // lets check if the binary directory is on the path
        if (!ProcessUtil.folderIsOnPath(log, binDirectory)) {
            updateStartupScriptInstructions(binDirectory, binName);
        }

        return binaryFile;
    }

    // How to update your startup script if not in path
    private void updateStartupScriptInstructions(File binDir, String fileName) throws MojoExecutionException {
        String absolutePath = binDir.getAbsolutePath();
        String indent = "  ";
        log.warn("The %s bin folder %s is not on the PATH.", fileName, binDir.getAbsolutePath());
        log.warn("To easily start fabric8 CLI tools like [[B]]%s[[B]] directly, please adapt your environment:", fileName);
        if (getPlatform().equals(Platform.windows.name())) {
            log.info("Please add the following to PATH environment variable:");
            log.info("%s[[C]]set PATH=%%PATH%%;%s[[C]]",indent, absolutePath);
        } else {
            String setPathCmd = "export PATH=$PATH:" + absolutePath;
            log.info("Please add the following to your ~/.bashrc:");
            log.info("%s[[C]]%s[[C]]", indent, setPathCmd);

            File rcFile = getStartupScript();
            if (rcFile != null) {
                updateStartupScript(rcFile, setPathCmd);
            }
        }
    }

    // Ask user whether to update startup script and do it if requested.
    private void updateStartupScript(File rcFile, String setPathCmd) throws MojoExecutionException {
        if (!installBatchMode) {
            try {
                String answer = prompter.prompt("Would you like to add the path setting to your ~/" + rcFile.getName() + " now? (Y/n)");
                if (answer != null && answer.trim().isEmpty() || answer.trim().toUpperCase().startsWith("Y")) {
                    addToStartupScript(rcFile, setPathCmd);
                    log.info("Updated %s. Please type the following command to update your current shell:", rcFile);
                    log.info("     [[C]]source ~/%s[[C]]", rcFile.getName());
                }
            } catch (PrompterException e) {
                log.warn("Failed to ask user prompt: %s", e);
            }
        } else {
            log.warn("Cannot update startup script when running in batch mode");
        }
    }

    // Try several shell startup scripts, return the first
    private File getStartupScript() {
        File homeDir = new File(System.getProperty("user.home", "."));
        for (String fileName : new String[]{".bashrc", ".zshrc", ".profile", ".bash_profile"}) {
            File testFile = new File(homeDir, fileName);
            if (testFile.exists() && testFile.isFile()) {
                return testFile;
            }
        }
        return null;
    }

    // Update startup script
    private void addToStartupScript(File rcFile, String text) throws MojoExecutionException {
        try (FileWriter writer = new FileWriter(rcFile, true)) {
            writer.append("\n");
            writer.append("# Added by fabric8-maven-plugin at " + new Date() + "\n");
            writer.append(text + "\n");
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to append to " + rcFile + ": " + e, e);
        }
    }

    // Check for a valide ~/.fabric8/bin
    private void validateDir(File dir) throws MojoExecutionException {
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new MojoExecutionException(String.format("Failed to create directory %s. Do you have permission on this folder?", dir));
            }
        } else if (!dir.isDirectory()) {
            throw new MojoExecutionException(String.format("%s exists but is not a directory", dir));
        }
    }

    // Download gofabric8
    protected void downloadExecutable(String versionUrl, String downloadUrl, File downloadDir, File destFile, String fileName) throws MojoExecutionException {

        // Download to a temporary file
        File tempFile = downloadToTempFile(versionUrl,downloadUrl, downloadDir, fileName);

        // Move into it's destination place in ~/.fabric8/bin
        moveFile(tempFile, destFile, fileName);
    }

    // First download in a temporary place
    private File downloadToTempFile(String versionUrl, String downloadUrlFormat, File downloadDir, String fileName) throws MojoExecutionException {
        // TODO: Very checksum and potentially signature
        File destFile = createDownloadFile(downloadDir, fileName);
        URL downloadUrl = getDownloadUrl(versionUrl, downloadUrlFormat, fileName);
        IoUtil.download(log, downloadUrl, destFile);
        return destFile;
    }


    // Where to put the initial download of gofabric8
    private File createDownloadFile(File downloadLocation, String fileName) throws MojoExecutionException {
        try {
            File downloadDir = Files.createTempDirectory(downloadLocation.toPath(), "download").toFile();
            downloadDir.deleteOnExit();
            File ret = new File(downloadDir, fileName);
            ret.deleteOnExit();
            log.debug("Downloading %s to temporary file %s", fileName, ret);
            return ret;
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create a temporary file for the download");
        }
    }

    // Create download URL + log
    private URL getDownloadUrl(String versionUrl, String downloadUrl, String fileName) throws MojoExecutionException {
        String version = getBinaryFileVersion(versionUrl);

        String platform = getPlatform().name();
        String arch = getArchitecture().name();
        String releaseUrl = String.format(downloadUrl, version, platform, arch);
        if (platform.equalsIgnoreCase("windows")) {
            releaseUrl += ".exe";
        }
        log.info("Downloading %s:", fileName);
        log.info("   Version:      [[B]]%s[[B]]", version);
        log.info("   Platform:     [[B]]%s[[B]]", platform);
        log.info("   Architecture: [[B]]%s[[B]]", arch);

        try {
            return new URL(releaseUrl);
        } catch (MalformedURLException e) {
            throw new MojoExecutionException("Failed to create URL from " + releaseUrl + ": " + e, e);
        }
    }

    // Move binary to its final place
    private void moveFile(File tempFile, File destFile, String fileName) throws MojoExecutionException {
        if (!tempFile.renameTo(destFile)) {
            // lets try copy it instead as this could be an odd linux issue with renaming files
            try {
                IOHelpers.copy(new FileInputStream(tempFile), new FileOutputStream(destFile));
                log.info("Downloaded %s to %s", fileName, destFile);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to copy temporary file " + tempFile + " to " + destFile + ": " + e, e);
            }
        }
        if (!destFile.setExecutable(true)) {
            throw new MojoExecutionException("Cannot make " + destFile + " executable");
        }
    }

    // Download version for gofabric8
    private String getBinaryFileVersion(String versionUrl) throws MojoExecutionException {
        try {
            String version = IOHelpers.readFully(new URL(versionUrl));
            return version;
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to load version from " + versionUrl + ". " + e, e);
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

    protected void executeGoFabric8Command(File command, String... args) throws MojoExecutionException {
        executeCommand(command, GOFABRIC8, ArrayUtils.addAll(args, BATCH_ARGUMENT));

    }

    protected void executeCommand(File command, String binName, String... args) throws MojoExecutionException {
        // Be sure to run in batch mode
        List<String> argList = new ArrayList<>(Arrays.asList(args));
        String argLine = Strings.join(argList, " ");
        log.info("Running %s %s", command, argLine);

        String message = command.getName() + " " + argLine;
        try {
            int result = ProcessUtil.runCommand(createExternalProcessLogger("[[B]]"+binName+"[[B]] "), command, argList);
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
        } else if (mode != null && mode == PlatformMode.openshift) {
            return true;
        } else {
            return false;
        }
    }
}
