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

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpec;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.KubernetesResourceUtil;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.utils.Strings;
import org.apache.maven.plugin.MojoExecutionException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.fabric8.kubernetes.api.KubernetesHelper.getKind;
import static io.fabric8.utils.Lists.notNullList;

/**
 * Merges local resources with dependent resources which have the same name.
 * <p>
 * e.g. if you wish to take a dependent set of resources and tweak them a little bit.
 */
public class MergeEnricher extends BaseEnricher {

    public MergeEnricher(EnricherContext buildContext) {
        super(buildContext, "fmp-merge");
    }

    @Override
    public void adapt(KubernetesListBuilder builder) {
        List<HasMetadata> items = notNullList(builder.getItems());
        Map<String, Map<String, HasMetadata>> kindMaps = new HashMap<>();

        List<HasMetadata> removeList = new ArrayList<>();
        for (HasMetadata item : items) {
            String kind = getKind(item);
            String name = KubernetesHelper.getName(item);
            Map<String, HasMetadata> map = kindMaps.get(kind);
            if (map == null) {
                map = new HashMap<>();
                kindMaps.put(kind, map);
            }
            HasMetadata old = map.get(name);
            if (old != null) {
                HasMetadata removeItem = mergeEntities(old, item);
                if (removeItem != null) {
                    removeList.add(removeItem);
                }
            } else {
                map.put(name, item);
            }
        }
        items.removeAll(removeList);
        builder.withItems(items);
    }

    private HasMetadata mergeEntities(HasMetadata item1, HasMetadata item2) {
        if (isMergeEnabled()) {
            if (item1 instanceof Deployment && item2 instanceof Deployment) {
                HasMetadata answer = item1;
                Deployment resource1 = (Deployment) item1;
                Deployment resource2 = (Deployment) item2;
                DeploymentSpec spec1 = resource1.getSpec();
                DeploymentSpec spec2 = resource2.getSpec();
                if (spec1 == null) {
                    resource1.setSpec(spec2);
                } else {
                    PodTemplateSpec template1 = spec1.getTemplate();
                    PodTemplateSpec template2 = spec2.getTemplate();
                    if (template1 == null) {
                        spec1.setTemplate(template2);
                    } else {
                        PodSpec podSpec1 = template1.getSpec();
                        PodSpec podSpec2 = template2.getSpec();
                        if (podSpec1 == null) {
                            template1.setSpec(podSpec2);
                        } else {
                            String defaultName = null;
                            PodTemplateSpec updateTemplate = template1;
                            if (isLocalCustomisation(podSpec1)) {
                                updateTemplate = template2;
                                PodSpec tmp = podSpec1;
                                podSpec1 = podSpec2;
                                podSpec2 = tmp;
                            } else {
                                answer = item2;
                            }
                            PodSpecBuilder podSpecBuilder = new PodSpecBuilder(podSpec1);
                            KubernetesResourceUtil.mergePodSpec(podSpecBuilder, podSpec2, defaultName);
                            updateTemplate.setSpec(podSpecBuilder.build());
                            return answer;
                        }
                    }
                }
                log.info("Merging 2 resources for " + getKind(item1) + " " + KubernetesHelper.getName(item1) + " from " + KubernetesResourceUtil.location(item1) + " and " + KubernetesResourceUtil.location(item2) + " and removing " + KubernetesResourceUtil.location(answer));
                return answer;
            }
            /*
                    log.info("Have 2 resources for " + getKind(item1) + " " + KubernetesHelper.getName(item1) + " assuming they are the same and picking one!");
                    // for now lets just pick one and assume they are identical
                    // TODO lets improve this with real merges!
                    return item1;
            */
        }
        // we expect lots of duplicates when making an app catalog as we have the composites and individual manifests
        try {
            if (!getContext().runningWithGoal("fabric8:app-catalog")) {
                log.warn("Duplicate resources for " + getKind(item1) + " " + KubernetesHelper.getName(item1) + " from " + KubernetesResourceUtil.location(item1) + " and " + KubernetesResourceUtil.location(item2));
            }
        } catch (MojoExecutionException e) {
            log.warn("Failed to check if generated an app-catalog: " + e, e);
        }
        return null;
    }

    protected boolean isMergeEnabled() {
        return Configs.asBoolean(getConfig(Config.enabled));
    }

    // lets use presence of an image name as a clue that we are just enriching things a little
    // rather than a full complete manifest
    // we could also use an annotation?
    private boolean isLocalCustomisation(PodSpec podSpec) {
        List<Container> containers = notNullList(podSpec.getContainers());
        for (Container container : containers) {
            if (Strings.isNotBlank(container.getImage())) {
                return false;
            }
        }
        return true;
    }

    // Available configuration keys
    private enum Config implements Configs.Key {
        enabled;

        protected String d;

        public String def() {
            return d;
        }
    }
}
