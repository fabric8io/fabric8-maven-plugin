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

import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.utils.Strings;
import org.apache.maven.project.MavenProject;

/**
 */
public class Containers {
  public static String getKubernetesContainerName(MavenProject project, ImageConfiguration imageConfig) {
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
}
