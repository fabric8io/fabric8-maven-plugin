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
package io.fabric8.maven.enricher.fabric8;


import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;

public class DekorateEnricher extends BaseEnricher {

    private static final String ENRICHER_NAME = "f8-dekorate";
    private static final String INPUT_DIR = "dekorate.input.dir";
    private static final String OUTPUT_DIR = "dekorate.output.dir";
    private static final String[] REQUIRED_CLASSES = new String[]{
        "io.dekorate.annotation.Dekorate"
    };

    public DekorateEnricher(MavenEnricherContext enricherContext) {
        super(enricherContext, ENRICHER_NAME);
    }

    @Override
    public void create(PlatformMode platformMode, KubernetesListBuilder builder) {
        if (getContext().getProjectClassLoaders().isClassInCompileClasspath(true, REQUIRED_CLASSES)) {
            System.setProperty(INPUT_DIR, "META-INF/fabric8");
            System.setProperty(OUTPUT_DIR, "META-INF/fabric8-dekorate");
        }
    }
}
