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
package io.fabric8.maven.core.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.Controller;
import io.fabric8.maven.core.util.ProcessUtil;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.utils.Strings;

/**
 * @author nicola
 * @since 28/03/2017
 */
public class PortForwardService {

    private ClientToolsService clientToolsService;

    private Logger log;

    public PortForwardService(ClientToolsService clientToolsService, Logger log) {
        this.clientToolsService = clientToolsService;
        this.log = log;
    }

    public void forwardPort(Controller controller, Logger externalProcessLogger, String pod, int remotePort, int localPort) throws Fabric8ServiceException {
        File command = clientToolsService.getKubeCtlExecutable(controller);
        log.info("Port forwarding to port " + remotePort + " on pod " + pod + " using command " + command);

        List<String> args = new ArrayList<>();
        args.add("port-forward");
        args.add(pod);
        args.add(localPort + ":" + remotePort);

        String commandLine = command + " " + Strings.join(args, " ");
        log.verbose("Executing command " + commandLine);
        try {
            ProcessUtil.runCommand(externalProcessLogger, command, args, true);
        } catch (IOException e) {
            throw new Fabric8ServiceException("Error while executing the port-forward command", e);
        }
    }
}
