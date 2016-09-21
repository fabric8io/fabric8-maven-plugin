/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.maven.plugin;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.ServiceNames;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.OpenShiftNotAvailableException;
import io.fabric8.utils.Strings;
import io.fabric8.utils.URLUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.fusesource.jansi.Ansi;

import java.net.URL;
import java.net.UnknownHostException;

public abstract class AbstractFabric8Mojo extends AbstractMojo {

    public static final Ansi.Color COLOR_POD_LOG = Ansi.Color.BLUE;
    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    protected MavenSession session;

    // Whether to use color
    @Parameter(property = "fabric8.useColor", defaultValue = "true")
    protected boolean useColor;

    // For verbose output
    @Parameter(property = "fabric8.verbose", defaultValue = "false")
    protected boolean verbose;

    protected Logger log;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        log = new AnsiLogger(getLog(), useColor, verbose, "F8> ");
        executeInternal();
    }

    public abstract void executeInternal() throws MojoExecutionException, MojoFailureException;



    protected String getProperty(String key) {
        String value = System.getProperty(key);
        if (value == null) {
            value = project.getProperties().getProperty(key);
        }
        return value;
    }


    protected Logger createExternalProcessLogger(String prefix) {
        if (useColor) {
            prefix += Ansi.ansi().fg(COLOR_POD_LOG);
        }
        return new AnsiLogger(getLog(), useColor, verbose, prefix);
    }

    protected void validateKubernetesMasterUrl(URL masterUrl) throws MojoFailureException {
        if (masterUrl == null || Strings.isNullOrBlank(masterUrl.toString())) {
            throw new MojoFailureException("Cannot find Kubernetes master URL. Have you started a cluster via `mvn fabric8:cluster-start` or connected to a remote cluster via `kubectl`?");
        }
    }

    protected void handleKubernetesClientException(KubernetesClientException e) throws MojoExecutionException {
        Throwable cause = e.getCause();
        if (cause instanceof UnknownHostException) {
            log.error("Could not connect to kubernetes cluster!");
            log.error("Have you started a local cluster via `mvn fabric8:cluster-start` or connected to a remote cluster via `kubectl`?");
            log.info("For more help see: http://fabric8.io/guide/getStarted/");
            log.error("Connection error: " + cause);

            String message = "Could not connect to kubernetes cluster. Have you started a cluster via `mvn fabric8:cluster-start` or connected to a remote cluster via `kubectl`? Error: " + cause;
            throw new MojoExecutionException(message, e);
        } else {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    protected OpenShiftClient getOpenShiftClientOrJenkinsShift(KubernetesClient kubernetes, String namespace) throws MojoExecutionException {
        OpenShiftClient openShiftClient = getOpenShiftClientOrNull(kubernetes);
        if (openShiftClient == null) {
            String jenkinshiftUrl = getJenkinShiftUrl(kubernetes, namespace);
            log.debug("Using jenknshift URL: " + jenkinshiftUrl);
            if (jenkinshiftUrl == null) {
                throw new MojoExecutionException("Could not find the service `" + ServiceNames.JENKINSHIFT + "` im namespace `" + namespace + "` on this kubernetes cluster " + kubernetes.getMasterUrl());
            }
            return KubernetesHelper.createJenkinshiftOpenShiftClient(jenkinshiftUrl);
        }
        if (openShiftClient == null) {
            throw new MojoExecutionException("Not connected to an OpenShift cluster and JenkinShift could not be found! Cluster: " + kubernetes.getMasterUrl());
        }
        return openShiftClient;
    }

    public static String getJenkinShiftUrl(KubernetesClient kubernetes, String namespace) {
        String jenkinshiftUrl = KubernetesHelper.getServiceURL(kubernetes, ServiceNames.JENKINSHIFT, namespace, "http", true);
        if (jenkinshiftUrl == null) {
            // the jenkinsshift URL is not external so lets use the fabric8 console
            String fabric8ConsoleURL = getFabric8ConsoleServiceUrl(kubernetes, namespace);
            if (Strings.isNotBlank(fabric8ConsoleURL)) {
                jenkinshiftUrl = URLUtils.pathJoin(fabric8ConsoleURL, "/k8s");
            }
        }
        return jenkinshiftUrl;
    }

    private static String getFabric8ConsoleServiceUrl(KubernetesClient kubernetes, String namespace) {
        return KubernetesHelper.getServiceURL(kubernetes, ServiceNames.FABRIC8_CONSOLE, namespace, "http", true);
    }


    protected OpenShiftClient getOpenShiftClientOrNull(KubernetesClient kubernetesClient) {
        try {
            return kubernetesClient.adapt(OpenShiftClient.class);
        } catch (OpenShiftNotAvailableException e) {
            // ignore
        }
        return null;
    }
}
