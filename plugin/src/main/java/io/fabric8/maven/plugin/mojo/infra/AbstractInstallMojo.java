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
import java.util.Date;

/**
 * Base class for install/tool related mojos
 */
public abstract class AbstractInstallMojo extends AbstractFabric8Mojo {
    private static final String gofabric8VersionURL = "https://raw.githubusercontent.com/fabric8io/gofabric8/master/version/VERSION";
    public static final String batchModeArgument = " --batch";
    public static final String GOFABRIC8 = "gofabric8";

    /**
     * Defines the kind of cluster such as `minishift`
     */
    @Parameter(property = "fabric8.cluster.kind")
    protected String clusterKind;


    @Parameter(property = "fabric8.dir", defaultValue = "${user.home}/.fabric8/bin")
    private File fabric8Dir;

    // X-TODO: Add update semantics similar to setup
    // X-TODO: Maybe combine fabric8:setup and fabric8:install
    // X-TODO: wonder if it should be renamed to fabric8:cluster-install?

    @Component
    private Prompter prompter;

    protected File installBinaries() throws MojoExecutionException {
        File file = ProcessUtil.findExecutable(log, GOFABRIC8);
        // X-TODO: Maybe allow for an update of gofabric8 itself ?
        if (file == null) {
            File binDir = getFabric8Dir();
            binDir.mkdirs();
            if (!binDir.isDirectory() || !binDir.exists()) {
                throw new MojoExecutionException("Failed to create directory: " + binDir + ". Do you have permission on this folder?");
            }
            file = new File(binDir, "gofabric8");
            if (!file.exists() || !file.isFile() || !file.canExecute()) {
                downloadGoFabric8(file);
            }

            // lets check if the binary directory is on the path
            if (!ProcessUtil.folderIsOnPath(log, binDir)) {
                String absolutePath = binDir.getAbsolutePath();
                String commandIndent = "  ";
                log.warn("Note that the fabric8 folder " + absolutePath + " is not on the PATH!");
                if (getPlatform().equals(Platforms.WINDOWS)) {
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
            getLog().info("Found gofabric8 at: " + file);
            runCommand(file.getAbsolutePath() + " version" + batchModeArgument, "gofabric8 version" + batchModeArgument, "gofabric8");
        }
        return file;
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

    /**
     * Downloads the latest <code>gofabric8</code> binary and runs the version command
     * to check the binary works before installing it.
     */
    protected void downloadGoFabric8(File destFile) throws MojoExecutionException {
        File file = null;
        try {
            file = File.createTempFile("fabric8", ".bin");
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create a temporary file for the download");
        }
        log.debug("Downloading gofabric8 to temporary file: " + file.getAbsolutePath());

        String version;
        try {
            version = IOHelpers.readFully(new URL(gofabric8VersionURL));
            log.info("Downloading version " + version + " of gofabric8 to " + destFile + " ...");
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to load gofabric8 version from: " + gofabric8VersionURL + ". " + e, e);
        }

        String platform = getPlatform();
        String osArch = System.getProperty("os.arch");
        String arch = Architectures.AMD64;
        if (osArch.toLowerCase().contains("arm")) {
            arch = Architectures.ARM;
        }
        String releaseUrl = "https://github.com/fabric8io/gofabric8/releases/download/v" + version + "/gofabric8-" + platform + "-" + arch;
        if (platform.equals("windows")) {
            releaseUrl += ".exe";
        }
        URL downloadUrl;
        try {
            downloadUrl = new URL(releaseUrl);
        } catch (MalformedURLException e) {
            throw new MojoExecutionException("Failed to create URL: " + releaseUrl + ". " + e, e);
        }
        InputStream inputStream;
        try {
            inputStream = downloadUrl.openStream();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to open URL: " + releaseUrl + ". " + e, e);
        }
        try (OutputStream out = new FileOutputStream(file)) {
            IOHelpers.copy(inputStream, out);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to download URL: " + releaseUrl + " to file: " + file + ". " + e, e);
        }
        file.setExecutable(true);
        // TODO: Very checksum and potentially signature

        // lets check we can execute the binary before we try to replace it if it already exists
        runCommand(file.getAbsolutePath() + " version" + batchModeArgument, "gofabric8 version" + batchModeArgument, "gofabric8");

        boolean result = file.renameTo(destFile);
        if (!result) {
            // lets try copy it instead as this could be an odd linux issue with renaming files
            try {
                IOHelpers.copy(new FileInputStream(file), new FileOutputStream(destFile));
                destFile.setExecutable(true);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to copy temporary file " + file + " to " + destFile + ": " + e, e);
            }
        }
        log.info("Downloaded gofabric8 version " + version + " platform: " + platform + " arch:" + arch + " on: " + System.getProperty("os.name") + " " + arch + " to: " + destFile);
    }

    protected String getPlatform() {
        String osName = System.getProperty("os.name");
        String platform = Platforms.LINUX;
        if (osName.contains("OS X") || osName.contains("Mac ")) {
            platform = Platforms.DARWIN;
        } else if (osName.contains("Windows")) {
            platform = Platforms.WINDOWS;
        }
        return platform;
    }

    protected void runCommand(String commandLine, String message, String executableName) throws MojoExecutionException {
        log.info("Running command " + executableName + " " + commandLine);
        int result = -1;
        try {
            result = ProcessUtil.runCommand(createExternalProcessLogger("[[B]]" + executableName + "[[B]] "), commandLine, message);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to execute " + message + ". " + e, e);
        }
        if (result != 0) {
            throw new MojoExecutionException("Failed to execute " + message + " result was: " + result);
        }
    }

    protected File getFabric8Dir() {
        if (fabric8Dir == null) {
            fabric8Dir = new File(".");
        }
        fabric8Dir.mkdirs();
        return fabric8Dir;
    }

    protected boolean isMinishift() {
        if (Strings.isNotBlank(clusterKind)) {
            String text = clusterKind.toLowerCase().trim();
            return text.equals("minishift") || text.equals("openshift");
        }
        return false;
    }


    public static class Platforms {
        public static final String LINUX = "linux";
        public static final String DARWIN = "darwin";
        public static final String WINDOWS = "windows";
    }

    public static class Architectures {
        public static final String AMD64 = "amd64";
        public static final String ARM = "arm";
    }
}
