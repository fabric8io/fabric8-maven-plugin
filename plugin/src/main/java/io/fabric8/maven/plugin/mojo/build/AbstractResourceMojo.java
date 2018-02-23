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
package io.fabric8.maven.plugin.mojo.build;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.maven.core.util.KubernetesResourceUtil;
import io.fabric8.maven.core.util.ResourceClassifier;
import io.fabric8.maven.core.util.ResourceFileType;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.plugin.mojo.AbstractFabric8Mojo;
import io.fabric8.openshift.api.model.Template;
import io.fabric8.utils.Strings;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;

import static io.fabric8.maven.core.util.ResourceFileType.json;
import static io.fabric8.maven.core.util.ResourceFileType.yaml;

/**
 */
public abstract class AbstractResourceMojo extends AbstractFabric8Mojo {
    /**
     * The generated kubernetes and openshift manifests
     */
    @Parameter(property = "fabric8.targetDir", defaultValue = "${project.build.outputDirectory}/META-INF/fabric8")
    protected File targetDir;
    /**
     * The artifact type for attaching the generated resource file to the project.
     * Can be either 'json' or 'yaml'
     */
    @Parameter(property = "fabric8.resourceType")
    private ResourceFileType resourceFileType = yaml;
    @Component
    private MavenProjectHelper projectHelper;

    /**
     * Returns the Template if the list contains a single Template only otherwise returns null
     */
    protected static Template getSingletonTemplate(KubernetesList resources) {
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

    protected void writeResources(KubernetesList resources, ResourceClassifier classifier, Boolean generateRoute) throws MojoExecutionException {
        // write kubernetes.yml / openshift.yml
        File resourceFileBase = new File(this.targetDir, classifier.getValue());

        File file = writeResourcesIndividualAndComposite(resources, resourceFileBase, this.resourceFileType, log, generateRoute);

        // Attach it to the Maven reactor so that it will also get deployed
        projectHelper.attachArtifact(project, this.resourceFileType.getArtifactType(), classifier.getValue(), file);

        // TODO: Remove the following block when devops and other apps used by gofabric8 are migrated
        // to fmp-v3. See also https://github.com/fabric8io/fabric8-maven-plugin/issues/167
        if (this.resourceFileType.equals(yaml)) {
            // lets generate JSON too to aid migration from version 2.x to 3.x for packaging templates
            file = writeResource(resourceFileBase, resources, json);

            // Attach it to the Maven reactor so that it will also get deployed
            projectHelper.attachArtifact(project, json.getArtifactType(), classifier.getValue(), file);
        }
    }

    public static File writeResourcesIndividualAndComposite(KubernetesList resources, File resourceFileBase, ResourceFileType resourceFileType, Logger log, Boolean generateRoute) throws MojoExecutionException {

        //Storing the current items list to send it later in further functions.
        List<HasMetadata> oldItemList = resources.getItems();

        //Creating a new items list. This will be used to generate openshift.yml
        List<HasMetadata> newItemList = new ArrayList<>();

        if(!generateRoute) {

            //if flag is set false, this will remove the Route resource from resources list
            for (HasMetadata item : resources.getItems()) {
                if (item.getKind().equalsIgnoreCase("Route")) {
                    continue;
                }
                newItemList.add(item);
            }

            //update the resource with new list
            resources.setItems(newItemList);

        }

        // entity is object which will be sent to writeResource for openshift.yml
        // if generateRoute is false, this will be set to resources with new list
        // otherwise it will be set to resources with old list.
        Object entity = resources;

        // if the list contains a single Template lets unwrap it
        // in resources already new or old as per condition is set.
        // no need to worry about this for dropping Route.
        Template template = getSingletonTemplate(resources);
        if (template != null) {
            entity = template;
        }

        File file = writeResource(resourceFileBase, entity, resourceFileType);

        // write separate files, one for each resource item
        // Again setting the old resource list in resource, to make it as it was previously.
        // It is exclusively used at various places, we should not change it and keep it as it was after our work is done.

        resources.setItems(oldItemList);
        writeIndividualResources(resources, resourceFileBase, resourceFileType, log, generateRoute);
        return file;
    }

    private static void writeIndividualResources(KubernetesList resources, File targetDir, ResourceFileType resourceFileType, Logger log, Boolean generateRoute) throws MojoExecutionException {
        for (HasMetadata item : resources.getItems()) {
            String name = KubernetesHelper.getName(item);
            if (Strings.isNullOrBlank(name)) {
                log.error("No name for generated item %s", item);
                continue;
            }
            String itemFile = KubernetesResourceUtil.getNameWithSuffix(name, item.getKind());

            // Here we are writing individual file for all the resources.
            // if generateRoute is false and resource is route, we should not generate it.
            
            if (!(item.getKind().equalsIgnoreCase("Route") && generateRoute.equals(false))){
                File itemTarget = new File(targetDir, itemFile);
                writeResource(itemTarget, item, resourceFileType);
            }
        }
    }

    private static File writeResource(File resourceFileBase, Object entity, ResourceFileType resourceFileType) throws MojoExecutionException {
        try {
            return KubernetesResourceUtil.writeResource(entity, resourceFileBase, resourceFileType);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write resource to " + resourceFileBase + ". " + e, e);
        }
    }
}
