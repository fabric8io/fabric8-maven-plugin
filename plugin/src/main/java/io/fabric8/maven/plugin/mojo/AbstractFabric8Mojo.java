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
package io.fabric8.maven.plugin.mojo;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.ServiceNames;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.maven.core.util.GoalFinder;
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
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;

public abstract class AbstractFabric8Mojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    protected MavenSession session;

    // Whether to use color
    @Parameter(property = "fabric8.useColor", defaultValue = "true")
    protected boolean useColor;

    // To skip over the execution of the goal
    @Parameter(property = "fabric8.skip", defaultValue = "false")
    protected boolean skip;

    // For verbose output
    @Parameter(property = "fabric8.verbose", defaultValue = "false")
    protected boolean verbose;

    // Settings holding authentication info
    @Parameter(defaultValue = "${settings}", readonly = true)
    protected Settings settings;

    // Used for determining which mojos are called during a run
    @Component
    protected GoalFinder goalFinder;

    protected Logger log;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if( skip ) {
            return;
        }
        log = createLogger(" ");
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
        return createLogger(prefix + "[[s]]");
    }

    protected Logger createLogger(String prefix) {
        return new AnsiLogger(getLog(), useColor, verbose, !settings.getInteractiveMode(), "F8:" + prefix);
    }

    protected OpenShiftClient getOpenShiftClientOrJenkinsShift(KubernetesClient kubernetes, String namespace) throws MojoExecutionException {
        OpenShiftClient openShiftClient = getOpenShiftClientOrNull(kubernetes);
        if (openShiftClient == null) {
            String jenkinshiftUrl = getJenkinShiftUrl(kubernetes, namespace);
            log.debug("Using jenkinshift URL: " + jenkinshiftUrl);
            if (jenkinshiftUrl == null) {
                throw new MojoExecutionException("Could not find the service `" + ServiceNames.JENKINSHIFT + "` im namespace `" + namespace + "` on this kubernetes cluster " + kubernetes.getMasterUrl());
            }
            return KubernetesHelper.createJenkinshiftOpenShiftClient(jenkinshiftUrl);
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
