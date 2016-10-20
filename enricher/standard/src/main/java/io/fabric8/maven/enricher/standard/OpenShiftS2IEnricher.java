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

package io.fabric8.maven.enricher.standard;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.fabric8.maven.core.util.KubernetesResourceUtil.setEnvVar;

/**
 * Enricher for adding docker image environment variables to the manifest when using
 * S2I binary builds; since docker environment variables get lost when using S2I binary builds
 */
public class OpenShiftS2IEnricher extends BaseEnricher {

    public OpenShiftS2IEnricher(EnricherContext buildContext) {
        super(buildContext, "fmp-openshift-s2i");
    }

    @Override
    public void addMissingResources(KubernetesListBuilder builder) {
        if (!hasImageConfiguration()) {
            log.verbose("No image configurations found, skipping ...");
            return;
        }
        builder.accept(new TypedVisitor<PodSpecBuilder>() {

            @Override
            public void visit(PodSpecBuilder builder) {
                updatePodSpec(builder);
            }
        });
    }

    private void updatePodSpec(PodSpecBuilder builder) {
        if (isOpenShiftMode()) {
            log.debug("Now moving any docker environment variables from the image " +
                      "build configuration as in S2I binary mode!");
            List<Container> containers = builder.getContainers();
            if (containers != null) {
                boolean updated = false;
                for (Container container : containers) {
                    String imageName = container.getImage();
                    ImageConfiguration image = getImage(imageName);
                    if (image != null) {
                        BuildImageConfiguration buildConfiguration = image.getBuildConfiguration();
                        if (buildConfiguration != null) {
                            Map<String, String> env = buildConfiguration.getEnv();
                            if (env != null) {
                                for (Map.Entry<String, String> entry : env.entrySet()) {
                                    String name = entry.getKey();
                                    String value = entry.getValue();
                                    List<EnvVar> envVars = container.getEnv();
                                    if (envVars == null) {
                                        envVars = new ArrayList<>();
                                    }
                                    if (setEnvVar(envVars, name, value)) {
                                        updated = true;
                                        log.debug("Added to container " + imageName + " $" + name + "=" + value);
                                    }
                                    container.setEnv(envVars);
                                }
                            }
                        }
                    }
                }
                if (updated) {
                    builder.withContainers(containers);
                }
            }
        }
    }

    /**
     * Returns the image configuration for the given image name
     */
    private ImageConfiguration getImage(String imageName) {
        List<ImageConfiguration> images = getContext().getImages();
        for (ImageConfiguration imageConfiguration : images) {
            if (Objects.equals(imageName, imageConfiguration.getName())) {
                return imageConfiguration;
            }
        }
        log.warn("Could not find ImageConfiguration for image name: " + imageName);
        return null;
    }
}
