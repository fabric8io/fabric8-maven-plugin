/**
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
package io.fabric8.maven.plugin.mojo;

import io.fabric8.maven.core.access.ClusterConfiguration;
import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.utils.logging.MessageUtils;

import static io.fabric8.maven.core.util.Fabric8MavenPluginDeprecationUtil.logFabric8MavenPluginDeprecation;

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
    protected String verbose;

    // Settings holding authentication info
    @Parameter(defaultValue = "${settings}", readonly = true)
    protected Settings settings;

    @Parameter(property = "fabric8.logDeprecationWarning", defaultValue = "true")
    protected boolean logDeprecationWarning;

    @Parameter
    protected ClusterConfiguration access;

    protected Logger log;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if( skip ) {
            return;
        }
        log = createLogger(" ");
        logFabric8MavenPluginDeprecation(log, logDeprecationWarning);
        executeInternal();
        logFabric8MavenPluginDeprecation(log, logDeprecationWarning);
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
        return new AnsiLogger(getLog(), useColorForLogging(), verbose, !settings.getInteractiveMode(), "F8:" + prefix);
    }

    /**
     * Determine whether to enable colorized log messages
     * @return true if log statements should be colorized
     */
    private boolean useColorForLogging() {
        return useColor && MessageUtils.isColorEnabled()
            && !(EnvUtil.isWindows() && !EnvUtil.isMaven350OrLater(session));
    }


    protected ClusterConfiguration getClusterConfiguration() {
        final ClusterConfiguration.Builder clusterConfigurationBuilder = new ClusterConfiguration.Builder(access);

        return clusterConfigurationBuilder.from(System.getProperties())
            .from(project.getProperties()).build();
    }

}
