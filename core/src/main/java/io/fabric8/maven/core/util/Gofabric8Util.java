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

import java.io.File;
import java.io.IOException;
import java.util.*;

import io.fabric8.maven.docker.access.DockerConnectionDetector;
import io.fabric8.maven.docker.access.util.EnvCommand;
import io.fabric8.maven.docker.util.Logger;

/**
 * Tool for extracting the environment vars with gofabric8
 *
 * @author roland
 * @since 14/09/16
 */
public class Gofabric8Util {
    public static List<DockerConnectionDetector.DockerHostProvider> extractDockerHostProvider(Logger log) {
        if (findGofabric8(log) != null) {
            return Collections.<DockerConnectionDetector.DockerHostProvider>singletonList(
                new Gofabric8DockerHostProvider(log));
        } else {
            return null;
        }

    }

    public static File findGofabric8(Logger log) {
        return ProcessUtil.findExecutable(log, "gofabric8");
    }

    private static class Gofabric8DockerHostProvider implements DockerConnectionDetector.DockerHostProvider {

        private final Gofabric8EnvCommand command;
        private final Logger log;
        private Map<String, String> envMap;

        public Gofabric8DockerHostProvider(Logger log) {
            this.command = new Gofabric8EnvCommand(log);
            this.log = log;
        }

        @Override
        public synchronized DockerConnectionDetector.ConnectionParameter getConnectionParameter(String certPath) throws IOException {
            if (envMap == null) {
                envMap = command.getEnvironment();
            }
            String dockerHost = envMap.get("DOCKER_HOST");
            if (dockerHost == null) {
                return null;
            }
            log.info("DOCKER_HOST from gofabric8 : %s", dockerHost);
            return new DockerConnectionDetector.ConnectionParameter(dockerHost, certPath != null ? certPath : envMap.get("DOCKER_CERT_PATH"));
        }

        @Override
        public int getPriority() {
            // Lowest priority, meant as a fallback
            return -1;
        }
    }

    private static class Gofabric8EnvCommand extends EnvCommand {
        public Gofabric8EnvCommand(Logger log) {
            super(log, "export ");
        }

        @Override
        protected String[] getArgs() {
            return new String[] { "gofabric8", "docker-env" };
        }
    }
}