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

import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Scm;
import org.apache.maven.project.MavenProject;

import java.util.HashMap;
import java.util.Map;

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
    static final String SCM_CONNECTION = "fabric8.io/scm-con-url";
    static final String SCM_DEVELOPER_CONNECTION = "fabric8.io/scm-devcon-url";
    static final String SCM_TAG = "fabric8.io/scm-tag";
    static final String SCM_URL = "fabric8.io/scm-url";

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
                    annotations.put(SCM_CONNECTION, connectionUrl);
                }
                if (StringUtils.isNotEmpty(devConnectionUrl)) {
                    annotations.put(SCM_DEVELOPER_CONNECTION, devConnectionUrl);
                }
                if (StringUtils.isNotEmpty(tag)) {
                    annotations.put(SCM_TAG, tag);
                }
                if (StringUtils.isNotEmpty(url)) {
                    annotations.put(SCM_URL, url);
                }
            }
        }
        return annotations;
    }

    private boolean hasScm(MavenProject project) {
        return project.getScm() != null;
    }

}
