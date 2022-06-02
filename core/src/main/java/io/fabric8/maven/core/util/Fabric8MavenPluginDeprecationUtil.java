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
package io.fabric8.maven.core.util;

import io.fabric8.maven.docker.util.Logger;

public class Fabric8MavenPluginDeprecationUtil {
  private Fabric8MavenPluginDeprecationUtil() { }

  public static void logFabric8MavenPluginDeprecation(Logger logger, boolean logDeprecationWarning) {
    if (logDeprecationWarning) {
      logger.warn("Fabric8 Maven Plugin has been deprecated and is no longer supported.\n\n" +
          "Please consider migrating to Eclipse JKube (https://github.com/eclipse/jkube) plugins:\n" +
          "  - Kubernetes Maven Plugin (https://www.eclipse.org/jkube/docs/kubernetes-maven-plugin)\n" +
          "  - OpenShift Maven Plugin (https://www.eclipse.org/jkube/docs/openshift-maven-plugin/)\n" +
          "You can read the Migration Guide for more details (https://www.eclipse.org/jkube/docs/migration-guide/)\n\n" +
          "Note: To disable this warning use `-Dfabric8.logDeprecationWarning=false`.");
    }
  }
}
