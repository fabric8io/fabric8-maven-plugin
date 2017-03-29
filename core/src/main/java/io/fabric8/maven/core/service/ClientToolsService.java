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

import io.fabric8.kubernetes.api.Controller;
import io.fabric8.maven.core.util.ProcessUtil;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.openshift.client.OpenShiftClient;

/**
 * A service that manages the client tools.
 * Try to avoid using this class, as support for client tools may be removed in the future.
 */
public class ClientToolsService {

    private Logger log;

    public ClientToolsService(Logger log) {
        this.log = log;
    }

    public File getKubeCtlExecutable(Controller controller) {
        OpenShiftClient openShiftClient = controller.getOpenShiftClientOrNull();
        String command = openShiftClient != null ? "oc" : "kubectl";

        String missingCommandMessage;
        File file = ProcessUtil.findExecutable(log, command);
        if (file == null && command.equals("oc")) {
            file = ProcessUtil.findExecutable(log, command);
            missingCommandMessage = "commands oc or kubectl";
        } else {
            missingCommandMessage = "command " + command;
        }
        if (file == null) {
            throw new IllegalStateException("Could not find " + missingCommandMessage +
                    ". Please try running `mvn fabric8:install` to install the necessary binaries and ensure they get added to your $PATH");
        }
        return file;
    }

}
