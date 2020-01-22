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
package io.fabric8.maven.plugin.mojo.build;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.core.util.ProjectClassLoaders;
import io.fabric8.maven.core.util.ResourceFileType;
import io.fabric8.maven.core.util.ResourceUtil;
import io.fabric8.maven.core.util.kubernetes.KubernetesHelper;
import io.fabric8.maven.core.util.kubernetes.KubernetesResourceUtil;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.openshift.api.model.Template;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.util.List;

class ResourceMojoUtil {

    static final String DEFAULT_RESOURCE_LOCATION = "META-INF/fabric8";
    private static final String[] DEKORATE_CLASSES = new String[]{
        "io.dekorate.annotation.Dekorate"
    };

    private ResourceMojoUtil() {
    }

    static boolean useDekorate(MavenProject project) {
        return new ProjectClassLoaders(MavenUtil.getCompileClassLoader(project))
            .isClassInCompileClasspath(true, DEKORATE_CLASSES);
    }

    /**
     * Returns the Template if the list contains a single Template only otherwise returns null
     */
    static Template getSingletonTemplate(KubernetesList resources) {
        // if the list contains a single Template lets unwrap it
        if (resources != null) {
            List<HasMetadata> items = resources.getItems();
            if (items != null && items.size() == 1) {
                HasMetadata singleEntity = items.get(0);
                if (singleEntity instanceof Template) {
                    return (Template) singleEntity;
                }
            }
        }
        return null;
    }

    static void writeIndividualResources(KubernetesList resources, File targetDir,
        ResourceFileType resourceFileType, Logger log) throws MojoExecutionException {
        for (HasMetadata item : resources.getItems()) {
            String name = KubernetesHelper.getName(item);
            if (StringUtils.isBlank(name)) {
                log.error("No name for generated item %s", item);
                continue;
            }
            String itemFile = KubernetesResourceUtil.getNameWithSuffix(name, item.getKind());

            File itemTarget = new File(targetDir, itemFile);
            writeResource(itemTarget, item, resourceFileType);
        }
    }

    static File writeResource(File resourceFileBase, Object entity, ResourceFileType resourceFileType)
        throws MojoExecutionException {
        try {
            return ResourceUtil.save(resourceFileBase, entity, resourceFileType);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write resource to " + resourceFileBase + ". " + e, e);
        }
    }
}
