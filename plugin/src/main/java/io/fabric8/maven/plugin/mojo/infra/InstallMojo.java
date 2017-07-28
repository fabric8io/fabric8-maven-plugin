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
package io.fabric8.maven.plugin.mojo.infra;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;

/**
 * Installs the necessary tools for working with clusters such as <code>gofabric8</code>
 */
@Mojo(name = "install", requiresProject = false)
public class InstallMojo extends AbstractInstallMojo {

    @Override
    public void executeInternal() throws MojoExecutionException, MojoFailureException {
        File file = installGofabric8IfNotAvailable();

        if (isMinishift()) {
            executeGoFabric8Command(file, "install",  "--minishift");
        } else {
            executeGoFabric8Command(file, "install");
        }

        installKomposeIfNotAvailable();
    }
}
