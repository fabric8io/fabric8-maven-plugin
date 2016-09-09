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
package io.fabric8.maven.plugin;

import io.fabric8.maven.core.util.ProcessUtil;
import io.fabric8.utils.IOHelpers;
import io.fabric8.utils.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.bouncycastle.asn1.x500.style.RFC4519Style.o;
import static org.eclipse.jgit.lib.ObjectChecker.type;
import static org.eclipse.jgit.transport.TransportProtocol.URIishField.PATH;

/**
 * Ensures that the fabric8 tools are installed on the current machine such as gofabric8
 */
@Mojo(name = "install", requiresProject = false)
public class InstallMojo extends AbstractFabric8Mojo {
    private static final String gofabric8VersionURL = "https://raw.githubusercontent.com/fabric8io/gofabric8/master/version/VERSION";

    @Parameter(property = "fabric8.dir", defaultValue = "${user.home}/fabric8")
    private File fabric8Dir;

    @Component
    private Prompter prompter;

    private List<File> pathDirectories;


    @Override
    public void executeInternal() throws MojoExecutionException, MojoFailureException {
        File file = findExecutable("gofabric8");
        if (file == null) {
            File binDir = getFabric8Dir();
            file = new File(binDir, "gofabric8");
            if (!file.exists() || !file.isFile() || !file.canExecute()) {
                downloadFabric8(file);
            }

            // lets check if the binary directory is on the path
            if (!folderIsOnPath(binDir)) {
                String absolutePath = binDir.getAbsolutePath();
                String commandIndent = "  ";
                log.warn("Note that the fabric8 folder " + absolutePath + " is not on the PATH!");
                if (getPlatform().equals(Platforms.WINDOWS)) {
                    log.warn("Please add the following to PATH environment variable:");
                    log.warn(commandIndent+ "set PATH=%PATH%;" + absolutePath);
                } else {
                    String bashrcLine = "export PATH=$PATH:" + absolutePath;

                    log.warn("Please add the following to your ~/.bashrc:");
                    log.warn(commandIndent + bashrcLine);

                    File bashrcFile = new File(getUserHome(), ".bashrc");
                    if (prompter != null && bashrcFile.exists() && bashrcFile.isFile()) {
                        String answer = null;
                        try {
                            answer = prompter.prompt("Would you like to add this line to your ~/.bashrc now? (Y/n)");
                        } catch (PrompterException e) {
                            log.warn("Failed to ask user prompt: " + e, e);
                        }
                        if (answer != null && answer.startsWith("Y")) {
                            addToBashRC(bashrcFile, bashrcLine);
                            log.info("Updated " + bashrcFile + ". Please type the following command to update your current shell:");
                            log.info(commandIndent + "source ~/.bashrc");
                        }
                    }
                }
            }
        } else {
            getLog().info("Found gofabric8 at: " + file);
        }

        runCommand(file.getAbsolutePath() + " version", "gofabric8 version");
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
     * Returns the platform we found
     */
    protected String downloadFabric8(File file) throws MojoExecutionException {
        String version;
        try {
            version = IOHelpers.readFully(new URL(gofabric8VersionURL));
            log.info("Downloading version " + version + " of gofabric8 to " + file + " ...");
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
        log.info("Downloaded gofabric8 version " + version + " platform: " + platform + " arch:" + arch + " on: " + System.getProperty("os.name") + " " + arch + " to: " + file);
        file.setExecutable(true);
        return platform;
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

    protected void runCommand(String commandLine, String message) throws MojoExecutionException {
        int result = -1;
        try {
            result = ProcessUtil.runCommand(log, commandLine, message);
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

    protected boolean isExecutableOnPath(String name) {
        return findExecutable(name) != null;
    }


    protected boolean folderIsOnPath(File dir) {
        String absolutePath = dir.getAbsolutePath();
        String canonicalPath = canonicalPath(dir);
        List<File> paths = getPathDirectories();
        for (File path : paths) {
            if (path.equals(dir) ||
                    path.getAbsolutePath().equals(absolutePath) ||
                    canonicalPath(path).equals(canonicalPath)) {
                return true;
            }
        }
        return false;
    }

    private String canonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            String absolutePath = file.getAbsolutePath();
            getLog().debug("Failed to get canonical path for " + absolutePath);
            return absolutePath;
        }
    }

    protected File findExecutable(String name) {
        File executable = null;
        List<File> pathDirectories = getPathDirectories();
        for (File directory : pathDirectories) {
            File file = new File(directory, name);
            if (file.exists() && file.isFile()) {
                if (!file.canExecute()) {
                    getLog().warn("Found " + file + " on the PATH but it is not executable!");
                } else {
                    executable = file;
                    break;
                }
            }

        }
        return executable;
    }


    protected List<File> getPathDirectories() {
        if (pathDirectories == null) {
            pathDirectories = new ArrayList<>();
            String pathText = System.getenv("PATH");
            if (Strings.isNullOrBlank(pathText)) {
                getLog().warn("The $PATH environment variable is empty! Usually you have a PATH defined to find binaries. " +
                        "Please report this to the fabric8 team: https://github.com/fabric8io/fabric8-maven-plugin/issues/new");
            } else {
                String[] pathTexts = pathText.split(File.pathSeparator);
                for (String text : pathTexts) {
                    File dir = new File(text);
                    if (!dir.exists()) {
                        getLog().debug("PATH entry: " + dir + " does not exist");
                    } else if (!dir.isDirectory()) {
                        getLog().debug("PATH entry: " + dir + " is a file not a directory");
                    } else {
                        pathDirectories.add(dir);
                    }
                }
            }
        }
        return pathDirectories;
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
