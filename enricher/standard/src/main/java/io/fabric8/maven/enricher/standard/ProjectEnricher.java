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

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MapUtil;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;
import org.apache.maven.project.MavenProject;

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

    public ProjectEnricher(EnricherContext buildContext) {
        super(buildContext, "fmp-project");
    }

    @Override
    public Map<String, String> getSelector(Kind kind) {
        return createLabels(kind.hasNoVersionInSelector());
    }

    @Override
    public void adapt(KubernetesListBuilder builder) {
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
        MavenProject project = getProject();
        Map<String, String> ret = new HashMap<>();

        boolean enableProjectLabel = Configs.asBoolean(getConfig(Config.useProjectLabel));
        if (enableProjectLabel) {
            ret.put("project", project.getArtifactId());
        } else {
            // default label is app
            ret.put("app", project.getArtifactId());
        }

        ret.put("group", project.getGroupId());
        ret.put("provider", "fabric8");
        if (!withoutVersion) {
            ret.put("version", project.getVersion());
        }
        return ret;
    }
}
