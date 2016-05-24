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

package io.fabric8.maven.plugin.handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.maven.docker.access.PortMapping;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.RunImageConfiguration;
import io.fabric8.maven.plugin.config.KubernetesConfiguration;
import io.fabric8.maven.plugin.config.VolumeConfiguration;
import io.fabric8.utils.Strings;
import org.apache.maven.project.MavenProject;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @author roland
 * @since 08/04/16
 */
class ContainerHandler {

    private final EnvVarHandler envVarHandler;
    private final ProbeHandler probeHandler;
    private final MavenProject project;

    public ContainerHandler(MavenProject project, EnvVarHandler envVarHandler, ProbeHandler probeHandler) {
        this.envVarHandler = envVarHandler;
        this.probeHandler = probeHandler;
        this.project = project;
    }

    List<Container> getContainers(KubernetesConfiguration config, List<ImageConfiguration> images) throws IOException {

        List<Container> ret = new ArrayList<>();
        for (ImageConfiguration imageConfig : images) {
            if (imageConfig.getBuildConfiguration() != null) {
                Container container = new ContainerBuilder()
                    .withName(getKubernetesContainerName(imageConfig))
                    .withImage(imageConfig.getName())
                    .withImagePullPolicy(getImagePullPolicy(config))
                    .withEnv(envVarHandler.getEnvironmentVariables(config.getEnv()))
                    .withSecurityContext(createSecurityContext(config))
                    .withPorts(getContainerPorts(imageConfig))
                    .withVolumeMounts(getVolumeMounts(config))
                    .withLivenessProbe(probeHandler.getProbe(config.getLiveness()))
                    .withReadinessProbe(probeHandler.getProbe(config.getReadiness()))
                    .build();
                ret.add(container);
            }
        }
        return ret;
    }


    private String getImagePullPolicy(KubernetesConfiguration config) {
        String pullPolicy = config.getImagePullPolicy();
        String version = project.getVersion();
        if (Strings.isNullOrBlank(pullPolicy) &&
            version != null && version.endsWith("SNAPSHOT")) {
            // TODO: Is that what we want ?
            return "PullAlways";
        }
        return pullPolicy;
    }

    private SecurityContext createSecurityContext(KubernetesConfiguration config) {
        return new SecurityContextBuilder()
            .withPrivileged(config.isContainerPrivileged())
            .build();
    }


    private String getKubernetesContainerName(ImageConfiguration imageConfig) {
        String alias = imageConfig.getAlias();
        if (alias != null) {
            return alias;
        }

        // lets generate it from the docker user and the camelCase artifactId
        String groupPrefix = null;
        String imageName = imageConfig.getName();
        if (Strings.isNotBlank(imageName)) {
            String[] paths = imageName.split("/");
            if (paths.length == 2) {
                groupPrefix = paths[0];
            } else if (paths.length == 3) {
                groupPrefix = paths[1];
            }
        }
        if (Strings.isNullOrBlank(groupPrefix)) {
            groupPrefix = project.getGroupId();
        }
        return groupPrefix + "-" + project.getArtifactId();
    }

    private List<VolumeMount> getVolumeMounts(KubernetesConfiguration config) {
        List<VolumeConfiguration> volumeConfigs = config.getVolumes();

        List<VolumeMount> ret = new ArrayList<>();
        if (volumeConfigs != null) {
            for (VolumeConfiguration volumeConfig : volumeConfigs) {
                List<String> mounts = volumeConfig.getMounts();
                if (mounts != null) {
                    for (String mount : mounts) {
                        ret.add(new VolumeMountBuilder()
                                    .withName(volumeConfig.getName())
                                    .withMountPath(mount)
                                    .withReadOnly(false).build());
                    }
                }
            }
        }
        return ret;
    }

    private List<ContainerPort> getContainerPorts(ImageConfiguration imageConfig) {
        BuildImageConfiguration buildConfig = imageConfig.getBuildConfiguration();
        List<String> ports = buildConfig.getPorts();
        if (ports != null) {
            List<ContainerPort> ret = new ArrayList<>();
            PortMapping portMapping = new PortMapping(ports, project.getProperties());
            JSONArray portSpecs = portMapping.toJson();
            for (int i = 0; i < portSpecs.length(); i ++) {
                JSONObject portSpec = portSpecs.getJSONObject(i);
                ret.add(extractContainerPort(portSpec));
            }
            return ret;
        } else {
            return null;
        }
    }

    private EditableContainerPort extractContainerPort(JSONObject portSpec) {
        ContainerPortBuilder portBuilder = new ContainerPortBuilder()
            .withContainerPort(portSpec.getInt("containerPort"));
        if (portSpec.has("hostPort")) {
            portBuilder.withHostPort(portSpec.getInt("hostPort"));
        }
        if (portSpec.has("protocol")) {
            portBuilder.withProtocol(portSpec.getString("protocol").toUpperCase());
        }
        if (portSpec.has("hostIP")) {
            portBuilder.withHostIP(portSpec.getString("hostIP"));
        }
        return portBuilder.build();
    }

}
