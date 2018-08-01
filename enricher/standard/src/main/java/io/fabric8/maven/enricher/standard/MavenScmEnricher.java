/*
 *    Copyright (c) 2016 Red Hat, Inc.
 *
 *    Red Hat licenses this file to you under the Apache License, version
 *    2.0 (the "License"); you may not use this file except in compliance
 *    with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *    implied.  See the License for the specific language governing
 *    permissions and limitations under the License.
 */

package io.fabric8.maven.enricher.standard;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.maven.core.util.kubernetes.Fabric8Annotations;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Scm;
import org.apache.maven.project.MavenProject;

/**
 * This enricher will add the maven &gt;scm&lt; related metadata as annotations
 * the typical values will be like
 * <ul>
 * <li>connection</li>
 * <li>developerConnection</li>
 * <li>url</li>
 * <li>tag</li>
 * </ul>
 *
 * @author kameshs
 */
public class MavenScmEnricher extends BaseEnricher {
    static final String ENRICHER_NAME = "f8-maven-scm";

    public MavenScmEnricher(EnricherContext buildContext) {
        super(buildContext, ENRICHER_NAME);
    }

    @Override
    public Map<String, String> getAnnotations(Kind kind) {
        Map<String, String> annotations = new HashMap<>();
        if (kind.isController() || kind == Kind.SERVICE) {
            MavenProject rootProject = getProject();
            if (hasScm(rootProject)) {
                Scm scm = rootProject.getScm();
                String connectionUrl = scm.getConnection();
                String devConnectionUrl = scm.getDeveloperConnection();
                String url = scm.getUrl();
                String tag = scm.getTag();

                if (StringUtils.isNotEmpty(connectionUrl)) {
                    annotations.put(Fabric8Annotations.SCM_CONNECTION.value(), connectionUrl);
                }
                if (StringUtils.isNotEmpty(devConnectionUrl)) {
                    annotations.put(Fabric8Annotations.SCM_DEVELOPER_CONNECTION.value(), devConnectionUrl);
                }
                if (StringUtils.isNotEmpty(tag)) {
                    annotations.put(Fabric8Annotations.SCM_TAG.value(), tag);
                }
                if (StringUtils.isNotEmpty(url)) {
                    annotations.put(Fabric8Annotations.SCM_URL.value(), url);
                }
            }
        }
        return annotations;
    }

    private boolean hasScm(MavenProject project) {
        return project.getScm() != null;
    }

}
