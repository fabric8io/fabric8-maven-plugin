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

import io.fabric8.utils.Closeables;
import io.fabric8.utils.Function;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.utils.Strings;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A helper class for running external processes
 */
public class ProcessUtil {

    public static int runCommand(final Logger log, String commands, String message) throws IOException {
        return runCommand(log, commands, new Function<String, Void>() {
            @Override
            public Void apply(String outputLine) {
                log.info(outputLine);
                return null;
            }
        },  new Function<String, Void>() {
            @Override
            public Void apply(String outputLine) {
                log.error(outputLine);
                return null;
            }
        }, message);
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
        File executable = null;
        List<File> pathDirectories = getPathDirectories(log);
        for (File directory : pathDirectories) {
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

}
