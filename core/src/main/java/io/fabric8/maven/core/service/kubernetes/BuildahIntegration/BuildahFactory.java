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
package io.fabric8.maven.core.service.kubernetes.BuildahIntegration;

import io.jshift.buildah.api.BuildahConfiguration;
import io.jshift.buildah.core.Buildah;
import io.jshift.buildah.core.InstallManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BuildahFactory {

    static final String BUILDAH = "buildah";
    static final String RUNC = "runc";
    static Path installationDirectory = Paths.get(System.getProperty("java.io.tmpdir"));
    static Path buildahLocalPath = Paths.get(System.getProperty("java.io.tmpdir"), BUILDAH);
    static Path runcLocalPath = Paths.get(System.getProperty("java.io.tmpdir"), RUNC);

    private static Buildah buildah;

    static Buildah createBuildah() {

        if (buildah == null) {
            final BuildahConfiguration buildahConfiguration = new BuildahConfiguration();
            buildahConfiguration.setInstallationDir(installationDirectory);

            buildah = new Buildah(buildahConfiguration);
        }

        return buildah;
    }

    static void removeBuildah() {
        try {
            final InstallManager installManager = new InstallManager();
            installManager.uninstall(buildahLocalPath);
            installManager.uninstall(runcLocalPath);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isBuildahCopied() {
        return Files.exists(buildahLocalPath);
    }

}
