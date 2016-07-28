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

package io.fabric8.maven.enricher.dependency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import org.apache.maven.artifact.Artifact;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/**
 * Enricher for embedding dependency descriptors to single package.
 *
 * @author jimmidyson
 * @since 14/07/16
 */
public class DependencyEnricher extends BaseEnricher {

  // Available configuration keys
  private enum Config implements Configs.Key {

    includeTransitive {{ d = "true"; }};

    public String def() { return d; } protected String d;
  }

  private static String DEPENDENCY_KUBERNETES_YAML = "/META-INF/fabric8/kubernetes.yml";
  private Set<File> dependencyArtifacts = new HashSet<>();

  public DependencyEnricher(EnricherContext buildContext) {
    super(buildContext, "f8-dependency");

    Set<Artifact> artifacts = isIncludeTransitive() ?
        buildContext.getProject().getArtifacts() : buildContext.getProject().getDependencyArtifacts();

    for (Artifact artifact : artifacts) {
      if (Artifact.SCOPE_COMPILE.equals(artifact.getScope()) && "jar".equals(artifact.getType())) {
        dependencyArtifacts.add(artifact.getFile());
      }
    }
  }

  @Override
  public void adapt(KubernetesListBuilder builder) {
    for (File artifact : dependencyArtifacts) {
      try {
        URL url = new URL("jar:" + artifact.toURI().toURL() + "!" + DEPENDENCY_KUBERNETES_YAML);
        InputStream is = url.openStream();
        KubernetesList resources = new ObjectMapper(new YAMLFactory()).readValue(is, KubernetesList.class);
        builder.addToItems(resources.getItems().toArray(new HasMetadata[0]));
      } catch (IOException e) {
        getLog().debug("Skipping " + artifact.toString() + ": " + e);
      }
    }
  }

  protected boolean isIncludeTransitive() {
    return Configs.asBoolean(getConfig(Config.includeTransitive));
  }

}
