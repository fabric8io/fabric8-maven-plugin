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

package io.fabric8.maven.core.handler;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.maven.core.config.ProbeConfig;
import io.fabric8.maven.core.util.Commandline;
import io.fabric8.utils.Strings;

/**
 * @author roland
 * @since 07/04/16
 */
public class ProbeHandler {

    public Probe getProbe(ProbeConfig probeConfig)  {
        if (probeConfig == null) {
            return null;
        }

        Probe probe = new Probe();
        Integer initialDelaySeconds = probeConfig.getInitialDelaySeconds();
        if (initialDelaySeconds != null) {
            probe.setInitialDelaySeconds(initialDelaySeconds);
        }
        Integer timeoutSeconds = probeConfig.getTimeoutSeconds();
        if (timeoutSeconds != null) {
            probe.setTimeoutSeconds(timeoutSeconds);
        }
        HTTPGetAction getAction = getHTTPGetAction(probeConfig.getGetUrl());
        if (getAction != null) {
            probe.setHttpGet(getAction);
            return probe;
        }
        ExecAction execAction = getExecAction(probeConfig.getExec());
        if (execAction != null) {
            probe.setExec(execAction);
            return probe;
        }
        TCPSocketAction tcpSocketAction = getTCPSocketAction(probeConfig.getTcpPort());
        if (tcpSocketAction != null) {
            probe.setTcpSocket(tcpSocketAction);
            return probe;
        }

        return null;
    }

    // ========================================================================================

    private HTTPGetAction getHTTPGetAction(String getUrl) {
        if (getUrl == null) {
            return null;
        }
        try {
            URL url = new URL(getUrl);
            return new HTTPGetAction(url.getHost(),
                                     null /* headers */,
                                     url.getPath(),
                                     new IntOrString(url.getPort()),
                                     url.getProtocol());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL " + getUrl + " given for HTTP GET readiness check");
        }
    }

    private TCPSocketAction getTCPSocketAction(String port) {
        if (port != null) {
            IntOrString portObj = new IntOrString(port);
            try {
                Integer portInt = Integer.parseInt(port);
                portObj.setIntVal(portInt);
            } catch (NumberFormatException e) {
                portObj.setStrVal(port);
            }
            return new TCPSocketAction(portObj);
        }
        return null;
    }

    private ExecAction getExecAction(String execCmd) {
        if (Strings.isNotBlank(execCmd)) {
            List<String> splitCommandLine = Commandline.translateCommandline(execCmd);
            if (!splitCommandLine.isEmpty()) {
                return new ExecAction(splitCommandLine);
            }
        }
        return null;
    }
}
