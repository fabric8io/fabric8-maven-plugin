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
package io.fabric8.maven.core.util;

import io.fabric8.maven.docker.util.Logger;
import io.fabric8.utils.Closeables;
import io.fabric8.utils.Function;
import io.fabric8.utils.Strings;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.fabric8.maven.docker.util.EnvUtil.isWindows;

/**
 * A helper class for running external processes
 */
public class ProcessUtil {

    public static int runCommand(final Logger log, String commands, String message) throws IOException {
        Function<String, Void> outputHandler = createOutputHandler(log);
        return runCommand(log, commands, outputHandler, createErrorHandler(log), message);
    }

    public static int runCommandAsync(final Logger log, String commands, String message) throws IOException {
        return runCommand(log, commands, createOutputHandler(log), createErrorHandler(log), message);
    }

    public static int runCommand(Logger log, String commands, Function<String, Void> outputHandler, Function<String, Void> errorHandler, String message) throws IOException {
        log.debug("Executing commands: " + commands);
        Process process;
        try {
            process = Runtime.getRuntime().exec(commands);
            processOutput(log, process.getInputStream(), outputHandler, message);
            processOutput(log, process.getErrorStream(), errorHandler, message);
        } catch (Exception e) {
            throw new IOException("Failed to execute process " + "stdin" + " for " +
                    message + ": " + e, e);
        }
        return process.exitValue();
    }

    public static int processCommandAsync(Process process, Logger log, String threadName, String message) throws InterruptedException {
        return processCommandAsync(process, log, threadName, createOutputHandler(log), createErrorHandler(log), message);
    }

    public static int processCommandAsync(final Process process, final Logger log, final String threadName, final Function<String, Void> outputHandler, final Function<String, Void> errorHandler, final String message) throws InterruptedException {
        startThread(new Thread(threadName + " read output") {
            @Override
            public void run() {
                try {
                    processOutput(log, process.getInputStream(), outputHandler, message);
                } catch (IOException e) {
                    log.error("Failed to read " + threadName + " output ." + e, e);
                }
            }
        });
        startThread(new Thread(threadName + " read error") {
            @Override
            public void run() {
                try {
                    processOutput(log, process.getErrorStream(), errorHandler, message);
                } catch (IOException e) {
                    log.error("Failed to read " + threadName + " error ." + e, e);
                }
            }
        });
        return process.waitFor();
    }

    private static void startThread(Thread thread) {
        thread.start();
    }

    protected static void processErrors(Logger log, InputStream inputStream, String message) throws Exception {
        readProcessOutput(log, inputStream, "stderr for ", message);
    }

    protected static void readProcessOutput(final Logger log, InputStream inputStream, final String prefix, final String message) throws Exception {
        Function<String, Void> function = new Function<String, Void>() {
            @Override
            public Void apply(String line) {
                log.debug("Error " + prefix + message + ": " + line);
                return null;
            }
        };
        processOutput(log, inputStream, function, prefix + message);
    }

    protected static void processOutput(Logger log, InputStream inputStream, Function<String, Void> function, String errrorMessage) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        try {
            while (true) {
                String line = reader.readLine();
                if (line == null) break;
                function.apply(line);
            }

        } catch (Exception e) {
            log.error("Failed to process " + errrorMessage + ": " + e, e);
            throw e;
        } finally {
            Closeables.closeQuietly(reader);
        }
    }

    public static File findExecutable(Logger log, String name) {
        List<File> pathDirectories = getPathDirectories(log);
        for (File directory : pathDirectories) {
            if( isWindows() ) {
                for (String extension : new String[]{".exe", ".bat", ".cmd"}) {
                    File file = new File(directory, name+extension);
                    if (file.exists() && file.isFile()) {
                        if (!file.canExecute()) {
                            log.warn("Found " + file + " on the PATH but it is not executable!");
                        } else {
                            return file;
                        }
                    }
                }
            }
            File file = new File(directory, name);
            if (file.exists() && file.isFile()) {
                if (!file.canExecute()) {
                    log.warn("Found " + file + " on the PATH but it is not executable!");
                } else {
                    return file;
                }
            }
        }
        return null;
    }

    public static boolean folderIsOnPath(Logger logger, File dir) {
        List<File> paths = getPathDirectories(logger);
        for (File path : paths) {
            if (canonicalPath(path).equals(canonicalPath(dir))) {
                return true;
            }
        }
        return false;
    }

    private static String canonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            String absolutePath = file.getAbsolutePath();
            return absolutePath;
        }
    }

    private static List<File> getPathDirectories(Logger log) {
        List<File> pathDirectories = new ArrayList<>();
        String pathText = System.getenv("PATH");
        if( isWindows() && pathText==null ) {
            // On windows, the PATH env var is case insensitive, force to upper case.
            for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
                if( entry.getKey().equalsIgnoreCase("PATH") ) {
                    pathText = entry.getValue();
                    break;
                }
            }
        }
        if (Strings.isNullOrBlank(pathText)) {
            log.warn("The $PATH environment variable is empty! Usually you have a PATH defined to find binaries. ");
            log.warn("Please report this to the fabric8 team: https://github.com/fabric8io/fabric8-maven-plugin/issues/new");
        } else {
            String[] pathTexts = pathText.split(File.pathSeparator);
            for (String text : pathTexts) {
                File dir = new File(text);
                if (dir.exists() && dir.isDirectory()) {
                    pathDirectories.add(dir);
                }
            }
        }
        return pathDirectories;
    }

    protected static Function<String, Void> createOutputHandler(final Logger log) {
        return new Function<String, Void>() {
            @Override
            public Void apply(String outputLine) {
                log.info(outputLine);
                return null;
            }
        };
    }

    protected static Function<String, Void> createErrorHandler(final Logger log) {
        return new Function<String, Void>() {
            @Override
            public Void apply(String outputLine) {
                log.error(outputLine);
                return null;
            }
        };
    }


}
