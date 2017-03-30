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
import io.fabric8.utils.Function;
import io.fabric8.utils.Strings;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.fabric8.maven.docker.util.EnvUtil.isWindows;

/**
 * A helper class for running external processes
 */
public class ProcessUtil {

    public static int runCommand(final Logger log, File command, List<String> args) throws IOException {
        return runCommand(log, command, args, false);
    }

    public static int runCommand(final Logger log, File command, List<String> args, boolean withShutdownHook) throws IOException {
        return runAsyncCommand(log, command, args, withShutdownHook).await();
    }

    public static ProcessExecutionContext runAsyncCommand(final Logger log, File command, List<String> args, boolean withShutdownHook) throws IOException {
        String[] commandWithArgs = prepareCommandArray(command.getAbsolutePath(), args);
        Process process = Runtime.getRuntime().exec(commandWithArgs);
        if (withShutdownHook) {
            addShutdownHook(log, process, command);
        }
        List<Thread> threads = startLoggingThreads(process, log, command.getName() + " " + Strings.join(args, " "));
        return new ProcessExecutionContext(process, threads, log);
    }

    private static void joinThreads(List<Thread> threads, Logger log) {
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                log.warn("Caught %s", e);
            }
        }
    }

    public static File findExecutable(Logger log, String name) {
        List<File> pathDirectories = getPathDirectories();
        for (File directory : pathDirectories) {
            for (String extension : isWindows() ? new String[]{ ".exe", ".bat", ".cmd", "" } : new String[] { "" }) {
                File file = new File(directory, name + extension);
                if (file.exists() && file.isFile()) {
                    if (!file.canExecute()) {
                        log.warn("Found %s on the PATH but it is not executable. Ignoring ...", file);
                    } else {
                        return file;
                    }
                }
            }
        }
        return null;
    }

    public static boolean folderIsOnPath(Logger logger, File dir) {
        List<File> paths = getPathDirectories();
        if (paths.size() == 0) {
            logger.warn("The $PATH environment variable is empty! Usually you have a PATH defined to find binaries.");
            logger.warn("Please report this to the fabric8 team: https://github.com/fabric8io/fabric8-maven-plugin/issues/new");
            return false;
        }

        for (File path : paths) {
            if (canonicalPath(path).equals(canonicalPath(dir))) {
                return true;
            }
        }
        return false;
    }

    // ==========================================================================================================

    private static void addShutdownHook(final Logger log, final Process process, final File command) {
        Runtime.getRuntime().addShutdownHook(new Thread(command.getName()) {
            @Override
            public void run() {
                if (process != null) {
                    log.info("Terminating process %s", command);
                    try {
                        process.destroy();
                    } catch (Exception e) {
                        log.error("Failed to terminate process %s", command);
                    }
                    /* Only available in Java 8: So disabled for now until we switch to Java 8
                    try {
                        if (process != null && process.isAlive()) {
                            process.destroyForcibly();
                        }
                    } catch (Exception e) {
                        log.error("Failed to forcibly terminate process %s", command);
                    }
                    */
                }
            }
        });
    }

    private static String[] prepareCommandArray(String command, List<String> args) {
        List<String> nArgs = args != null ? args : new ArrayList<String>();
        String[] commandWithArgs = new String[nArgs.size() + 1];
        commandWithArgs[0] = command;
        for (int i = 0; i < nArgs.size(); i++) {
            commandWithArgs[i+1] = nArgs.get(i);
        }
        return commandWithArgs;
    }

    private static void processOutput(InputStream inputStream, Function<String, Void> function) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                function.apply(line);
            }
        }
    }

    private static String canonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            String absolutePath = file.getAbsolutePath();
            return absolutePath;
        }
    }

    private static List<File> getPathDirectories() {
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

    private static List<Thread> startLoggingThreads(final Process process, final Logger log, final String commandDesc) {
        List<Thread> threads = new ArrayList<>();
        threads.add(startOutputLoggingThread(process, log, commandDesc));
        threads.add(startErrorLoggingThread(process, log, commandDesc));
        return threads;
    }

    private static Thread startErrorLoggingThread(final Process process, final Logger log, final String commandDesc) {
        Thread logThread = new Thread("[ERR] " + commandDesc) {
            @Override
            public void run() {
                try {
                    processOutput(process.getErrorStream(), createErrorHandler(log));
                } catch (IOException e) {
                    log.error("Failed to read error stream from %s : %s", commandDesc, e.getMessage());
                }
            }
        };
        logThread.setDaemon(true);
        logThread.start();
        return logThread;
    }

    private static Thread startOutputLoggingThread(final Process process, final Logger log, final String commandDesc) {
        Thread logThread = new Thread("[OUT] " + commandDesc) {
            @Override
            public void run() {
                try {
                    processOutput(process.getInputStream(), createOutputHandler(log));
                } catch (IOException e) {
                    log.error("Failed to read output stream from %s : %s", commandDesc, e.getMessage());
                }
            }
        };
        logThread.setDaemon(true);
        logThread.start();
        return logThread;
    }

    private static Function<String, Void> createOutputHandler(final Logger log) {
        return new Function<String, Void>() {
            @Override
            public Void apply(String outputLine) {
                log.info("%s", outputLine);
                return null;
            }
        };
    }

    private static Function<String, Void> createErrorHandler(final Logger log) {
        return new Function<String, Void>() {
            @Override
            public Void apply(String outputLine) {
                log.error("%s", outputLine);
                return null;
            }
        };
    }

    // =====================================================================================

    /**
     * Closeable class for holding a reference to a subprocess.
     */
    public static class ProcessExecutionContext implements Closeable {

        private Process process;

        private List<Thread> loggingThreads;

        private Logger log;

        public ProcessExecutionContext(Process process, List<Thread> loggingThreads, Logger log) {
            this.process = process;
            this.loggingThreads = loggingThreads;
            this.log = log;
        }

        public int await() {
            try {
                int answer = process.waitFor();
                joinThreads(loggingThreads, log);
                return answer;
            } catch (InterruptedException e) {
                return process.exitValue();
            }
        }

        @Override
        public void close() throws IOException {
            process.destroy();
            await();
        }
    }
}
