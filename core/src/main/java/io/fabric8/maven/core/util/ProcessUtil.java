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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

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
        }, message);
    }


    public static int runCommand(Logger log, String commands, Function<String, Void> outputHandler, String message) throws IOException {
        log.debug("Executing commands: " + commands);
        Process process;
        try {
            process = Runtime.getRuntime().exec(commands);
            processOutput(log, process.getInputStream(), outputHandler, message);
            processErrors(log, process.getErrorStream(), message);
        } catch (Exception e) {
            throw new IOException("Failed to execute process " + "stdin" + " for " +
                    message + ": " + e, e);
        }
        return process.exitValue();
    }

    protected static void processInput(Logger log, InputStream inputStream, String message) throws Exception {
        readProcessOutput(log, inputStream, "stdout for ", message);
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
}
