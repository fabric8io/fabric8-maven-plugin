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
package io.fabric8.maven.enricher.standard;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MapUtil;
import io.fabric8.maven.core.model.GroupArtifactVersion;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import io.fabric8.maven.enricher.api.Kind;
import java.util.HashMap;
import java.util.Map;

/**
 * Add project labels to any object.
 * For selectors, the 'version' part is removed.
 * <p>
 * The following labels are added:
 * <ul>
 * <li>version</li>
 * <li>app</li>
 * <li>group</li>
 * <li>provider (is set to fabric8)</li>
 * </ul>
 *
 * The "app" label can be replaced with the (old) "project" label using the "useProjectLabel" configuraiton option.
 *
 * The project labels which are already specified in the input fragments are not overridden by the enricher.
 *
 * @author roland
 * @since 01/04/16
 */
public class ProjectEnricher extends BaseEnricher {

    // Available configuration keys
    private enum Config implements Configs.Key {

        useProjectLabel {{ d = "false"; }};

        protected String d; public String def() {
            return d;
        }
    }

    public ProjectEnricher(MavenEnricherContext buildContext) {
        super(buildContext, "fmp-project");
    }

    @Override
    public Map<String, String> getSelector(Kind kind) {
        return createLabels(kind.hasNoVersionInSelector());
    }

    @Override
    public void adapt(PlatformMode platformMode, KubernetesListBuilder builder) {
        // Add to all objects in the builder
        builder.accept(new TypedVisitor<ObjectMetaBuilder>() {
            @Override
            public void visit(ObjectMetaBuilder element) {
                Map<String, String> labels = element.getLabels();
                MapUtil.mergeIfAbsent(labels, createLabels());
            }
        });
    }

    private Map<String, String> createLabels() {
        return createLabels(false);
    }

    private Map<String, String> createLabels(boolean withoutVersion) {
        Map<String, String> ret = new HashMap<>();

        boolean enableProjectLabel = Configs.asBoolean(getConfig(Config.useProjectLabel));
        final GroupArtifactVersion groupArtifactVersion = getContext().getGav();
        if (enableProjectLabel) {
            ret.put("project", groupArtifactVersion.getArtifactId());
        } else {
            // default label is app
            ret.put("app", groupArtifactVersion.getArtifactId());
        }

        ret.put("group", groupArtifactVersion.getGroupId());
        ret.put("provider", "fabric8");
        if (!withoutVersion) {
            ret.put("version", groupArtifactVersion.getVersion());
        }
        return ret;
    }
}
