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

import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.core.util.FatJarDetector;
import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.service.RegistryService;
import io.fabric8.maven.docker.util.Logger;
import io.jshift.buildah.core.Buildah;
import org.apache.maven.plugin.MojoExecutionException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BuildahBuildServiceUtil {


    public static void buildImage(BuildahBuildConfiguration buildConfiguration, Logger log) {

        String fromImage = buildConfiguration.getFrom();
        String targetImage = buildConfiguration.getTargetImage();
        Map<String, String> envMap  = buildConfiguration.getEnvMap();
        Map<String, String> labelMap  = buildConfiguration.getLabelMap();
        List<String> envList = null;
        List<String> labelList = null;
        if(envMap != null) {
            envList = envList(envMap);
        }

        if(labelMap != null) {
            labelList = labelList(labelMap);
        }

        List<String> entrypointList = new ArrayList<>();
        if(buildConfiguration.getEntryPoint() != null) {
            entrypointList = buildConfiguration.getEntryPoint().asStrings();
        }

        List<String> portList = buildConfiguration.getPorts();
        String targetDir = buildConfiguration.getTargetDir();
        Path fatJar = buildConfiguration.getFatJar();
        AuthConfig authConfig = buildConfiguration.getAuthConfig();

        buildImage(fromImage, targetImage, envList, labelList, portList, fatJar, targetDir, log, entrypointList);
    }

    public static List<String> envList(Map<String, String> envMap) {
        List<String> list = new ArrayList<>();

        for(String key : envMap.keySet()) {
            String value = envMap.get(key);
            String envVar = key + "=" + value;
            list.add(envVar);
        }
        return list;
    }

    public static List<String> labelList(Map<String, String> labelMap) {
        List<String> list = new ArrayList<>();

        for(String key : labelMap.keySet()) {
            String value = labelMap.get(key);
            String label = key + "=" + value;
            list.add(label);
        }
        return list;
    }

    protected static void buildImage(String baseImage, String targetImage, List<String> envList, List<String> labelList, List<String> portSet, Path fatJar, String targetDir, Logger log, List<String> entrypointList) {


        Buildah buildah = BuildahFactory.createBuildah();

        String con = buildah.createContainer(baseImage).build().execute();

        log.info("Container %s successfully built and pushed.", con);

        if(envList != null) {
            buildah.config(con).env(envList).build().execute();
        }

        if(labelList != null) {
            buildah.config(con).label(labelList).build().execute();
        }

        if(portSet != null) {
            buildah.config(con).port(portSet).build().execute();
        }

        if(!entrypointList.isEmpty()) {
            buildah.config(con).entrypoint(entrypointList).build().execute();
        }

        if (fatJar != null) {
            String jarPath = targetDir + "/" ;
            buildah.config(con).workingDir(jarPath).build().execute();
            buildah.copy(con, String.valueOf(fatJar)).destination(jarPath).build().execute();
        }

        buildah.commit(con).withImageName(targetImage).build().execute();
        buildah.rm().containerId(con).build().execute();
        log.info("Image successfullly build with %s as target image.", targetImage);
    }

    public static BuildahBuildConfiguration getBuildahBuildConfiguration(BuildService.BuildServiceConfig config, BuildImageConfiguration buildImageConfiguration, String fullImageName, Logger log) throws MojoExecutionException {

        io.fabric8.maven.docker.service.BuildService.BuildContext dockerBuildContext = config.getDockerBuildContext();
        RegistryService.RegistryConfig registryConfig = dockerBuildContext.getRegistryConfig();

        String targetDir = buildImageConfiguration.getAssemblyConfiguration().getTargetDir();

        AuthConfig authConfig = registryConfig.getAuthConfigFactory()
                .createAuthConfig(true, true, registryConfig.getAuthConfig(),
                        registryConfig.getSettings(), null, registryConfig.getRegistry());

        BuildahBuildConfiguration.Builder buildahBuildConfiguration = new BuildahBuildConfiguration.Builder(log).from(buildImageConfiguration.getFrom())
                .ports(buildImageConfiguration.getPorts())
                .targetImage(fullImageName)
                .envMap(buildImageConfiguration.getEnv())
                .labelMap(buildImageConfiguration.getLabels())
                .buildDirectory(config.getBuildDirectory())
                .targetDir(targetDir)
                .entrypoint(buildImageConfiguration.getEntryPoint());

        if (authConfig != null) {
            buildahBuildConfiguration.authConfig(authConfig);
        }

        return buildahBuildConfiguration.build();
    }

    public static Path getFatJar(String buildDir, Logger log) {

        FatJarDetector fatJarDetector = new FatJarDetector(buildDir);
        try {
            FatJarDetector.Result result = fatJarDetector.scan();
            if (result != null) {
                return result.getArchiveFile().toPath();
            }

        } catch (MojoExecutionException e) {
            log.error("MOJO Execution exception occured: %s", e);
            throw new UnsupportedOperationException();
        }
        return null;
    }
}