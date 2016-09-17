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

import io.fabric8.maven.docker.util.AnsiLogger;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.fusesource.jansi.Ansi;

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

    protected String getProperty(String key) {
        String value = System.getProperty(key);
        if (value == null) {
            value = project.getProperties().getProperty(key);
        }
        return value;
    }

    public abstract void executeInternal() throws MojoExecutionException, MojoFailureException;

    protected Logger createExternalProcessLogger(String prefix) {
        if (useColor) {
            prefix += Ansi.ansi().fg(COLOR_POD_LOG);
        }
        return new AnsiLogger(getLog(), useColor, verbose, prefix);
    }
}
