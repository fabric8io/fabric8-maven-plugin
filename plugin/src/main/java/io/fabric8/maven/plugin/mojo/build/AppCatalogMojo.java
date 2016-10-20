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

import io.fabric8.kubernetes.api.Annotations;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.maven.core.util.ClassUtil;
import io.fabric8.maven.core.util.ResourceClassifier;
import io.fabric8.openshift.api.model.Template;
import io.fabric8.openshift.api.model.TemplateBuilder;
import io.fabric8.utils.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.fabric8.kubernetes.api.KubernetesHelper.getName;
import static io.fabric8.kubernetes.api.KubernetesHelper.getOrCreateAnnotations;
import static io.fabric8.maven.core.util.Constants.APP_CATALOG_ANNOTATION;
import static io.fabric8.maven.core.util.ResourceClassifier.KUBERNETES_TEMPLATE;
import static io.fabric8.utils.Lists.notNullList;


/**
 * Generates an App Catalog for kubernetes and openshift
 * <br>
 * On OpenShift this just means getting the openshift.yml and if its not a Template wrapping it in an empty Template.
 * For Kubernetes this means checking if there is a kubernetes template.yaml and if so wrapping that in a ConfigMap
 * otherwise it uses the regular kubernetes.yaml file.
 */
@Mojo(name = "app-catalog", defaultPhase = LifecyclePhase.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE, requiresDependencyCollection = ResolutionScope.COMPILE)
public class AppCatalogMojo extends AbstractResourceMojo {
    /** defines the annotations copied from resources into the Template or ConfigMap in the app catalog */
    private Set<String> copiedAnnotations = new HashSet<>(Arrays.asList(Annotations.Builds.BUILD_URL,
            Annotations.Builds.BUILD_URL, Annotations.Builds.BUILD_ID, Annotations.Builds.DOCS_URL,
            Annotations.Builds.GIT_URL, Annotations.Builds.GIT_COMMIT, Annotations.Builds.GIT_BRANCH,
            Annotations.Builds.ICON_URL));

    public AppCatalogMojo() {
    }

    public void executeInternal() throws MojoExecutionException, MojoFailureException {
        List<HasMetadata> openshiftResources = new ArrayList<>();
        List<HasMetadata> kubernetesResources = new ArrayList<>();

        Map<URL, KubernetesResource> openshiftMap = loadYamlResourcesOnClassPath("META-INF/fabric8/openshift.yml");
        log.info("Found " + openshiftMap.size() + " openshift resources");
        for (Map.Entry<URL, KubernetesResource> entry : openshiftMap.entrySet()) {
            URL url = entry.getKey();
            KubernetesResource resource = entry.getValue();
            Template template = null;
            if (resource instanceof Template) {
                template = (Template) resource;
                getOrCreateAnnotations(template).put(APP_CATALOG_ANNOTATION, "true");
                log.debug("Found Template " + getName(template) + " with " + notNullList(template.getParameters()).size() + " parameters");
            } else {
                TemplateBuilder builder = new TemplateBuilder();
                boolean foundMetadata = false;
                if (resource instanceof HasMetadata) {
                    HasMetadata hasMetadata = (HasMetadata) resource;
                    ObjectMeta metadata = hasMetadata.getMetadata();
                    if (metadata != null) {
                        if (Strings.isNotBlank(metadata.getName())) {
                            foundMetadata = true;
                            getOrCreateAnnotations(hasMetadata).put(APP_CATALOG_ANNOTATION, "true");
                            builder.withMetadata(metadata);
                        }
                    }
                }
                if (!foundMetadata) {
                    Map<String, String> labels = new HashMap<>();
                    Map<String, String> annotations = new HashMap<>();
                    annotations.put(APP_CATALOG_ANNOTATION, "true");
                    String name = extractNameFromURL(url, labels);
                    if (name.equals("META-INF")) {
                        log.debug("Ignoring local build dependency " + url);
                        continue;
                    }
                    if (Strings.isNullOrBlank(name)) {
                        log.warn("Cannot generate a template name from URL: " + url);
                        continue;
                    }
                    populateLabelsFromResources(resource, labels);
                    populateAnnotationsFromResources(resource, annotations);
                    builder.withNewMetadata().withName(name).withLabels(labels).withAnnotations(annotations).endMetadata();
                }
                if (resource instanceof KubernetesList) {
                    KubernetesList list = (KubernetesList) resource;
                    List<HasMetadata> items = list.getItems();
                    if (items == null || items.isEmpty()) {
                        log.warn("Ignoring resource " + url + " as it contains a List which contains no items!");
                        continue;
                    }
                    builder.withObjects(items);
                }
                template = builder.build();
            }
            if (template != null) {
                openshiftResources.add(template);
            }
        }
        Map<String, Template> kubernetesTemplates = new HashMap<>();
        Map<URL, KubernetesResource> kubernetesTemplateMap = loadYamlResourcesOnClassPath("META-INF/fabric8/" + KUBERNETES_TEMPLATE.getValue() + ".yml");
        for (Map.Entry<URL, KubernetesResource> entry : kubernetesTemplateMap.entrySet()) {
            URL url = entry.getKey();
            KubernetesResource resource = entry.getValue();
            if (resource instanceof Template) {
                Template template = (Template) resource;
                String name = getName(template);
                if (Strings.isNullOrBlank(name)) {
                    log.warn("Ignoring Template from " + url + " as it has no name!");
                    continue;
                }
                if (kubernetesTemplates.containsKey(name)) {
                    log.warn("Found duplicate template named: " + name + " for url: " + url);
                }
                kubernetesTemplates.put(name, template);
            }
        }

        Set<String> kubernetesTemplateFileNames = new HashSet<>();
        Set<String> openshiftTemplateFileNames = new HashSet<>();

        Map<URL, KubernetesResource> kubernetesMap = loadYamlResourcesOnClassPath("META-INF/fabric8/kubernetes.yml");
        for (Map.Entry<URL, KubernetesResource> entry : kubernetesMap.entrySet()) {
            URL url = entry.getKey();
            KubernetesResource resource = entry.getValue();
            Map<String, String> labels = new HashMap<>();
            Map<String, String> annotations = new HashMap<>();
            String name = extractNameFromURL(url, labels);
            if (name.equals("META-INF")) {
                log.debug("Ignoring local build dependency " + url);
                continue;
            }
            if (Strings.isNullOrBlank(name)) {
                log.warn("Cannot generate a template name from URL: " + url);
                continue;
            }
            if (kubernetesTemplates.containsKey(name)) {
                log.info("Ignoring duplicate template " + name + " from url: " + url);
                continue;
            }
            populateLabelsFromResources(resource, labels);
            populateAnnotationsFromResources(resource, annotations);
            TemplateBuilder builder = new TemplateBuilder();
            builder.withNewMetadata().withName(name).withLabels(labels).withAnnotations(annotations).endMetadata();

            if (resource instanceof KubernetesList) {
                KubernetesList list = (KubernetesList) resource;
                List<HasMetadata> items = list.getItems();
                if (items == null || items.isEmpty()) {
                    log.warn("Ignoring resource " + url + " as it contains a List which contains no items!");
                    continue;
                }
                builder.withObjects(items);
            } else if (resource instanceof HasMetadata) {
                HasMetadata hasMetadata = (HasMetadata) resource;
                builder.withObjects(hasMetadata);
            }
            Template template = builder.build();
            if (template != null) {
                kubernetesTemplates.put(name, template);
                openshiftTemplateFileNames.add(name + "-template.yml");
            }
        }
        for (Map.Entry<String, Template> entry : kubernetesTemplates.entrySet()) {
            String name = entry.getKey();
            Template template = entry.getValue();
            String templateYaml = null;
            try {
                templateYaml = KubernetesHelper.toYaml(template);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to convert template " + name + " into YAML: " + e, e);
            }
            String catalogName = "catalog-" + name;

            Map<String, String> labels = new LinkedHashMap<>(KubernetesHelper.getLabels(template));
            Map<String, String> annotations = getOrCreateAnnotations(template);
            annotations.put(APP_CATALOG_ANNOTATION, "true");
            populateLabelsFromResources(template, labels);
            populateAnnotationsFromResources(template, annotations);
            labels.put("kind", "catalog");

            Map<String, String> data = new HashMap<>();
            data.put(catalogName + ".yml", templateYaml);
            kubernetesTemplateFileNames.add(catalogName + "-configmap.yml");

            ConfigMap configMap = new ConfigMapBuilder().
                    withNewMetadata().withName(catalogName).withLabels(labels).withAnnotations(annotations).endMetadata().
                    withData(data).build();
            kubernetesResources.add(configMap);
        }
        if (openshiftResources.isEmpty()) {
            log.warn("No OpenShift resources generated");
        } else {
            writeResources(new KubernetesListBuilder().withItems(openshiftResources).build(), ResourceClassifier.OPENSHIFT);
        }

        if (kubernetesResources.isEmpty()) {
            log.warn("No Kubernetes resources generated");
        } else {
            writeResources(new KubernetesListBuilder().withItems(kubernetesResources).build(), ResourceClassifier.KUBERNETES);
        }

        // lets remove the dependencies which are not app templates
        removeGeneratedFilesNotMatchingSuffix("kubernetes", kubernetesTemplateFileNames);
        removeGeneratedFilesNotMatchingSuffix("openshift", openshiftTemplateFileNames);
    }

    protected void removeGeneratedFilesNotMatchingSuffix(String kind, Set<String> validFileNames) {
        log.debug("Removing files not in set: " + validFileNames);
        File kubernetesOutputDir = new File(targetDir, kind);
        File[] files = kubernetesOutputDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!validFileNames.contains(file.getName())) {
                    file.delete();
                }
            }
        }
    }

    /**
     * Parses URLs of the form
     * <code>jar:file:/Users/jstrachan/.m2/repository/io/fabric8/devops/apps/manageiq/2.2.248/manageiq-2.2.248.jar!/META-INF/fabric8/openshift.yml</code>
     * to extract the name and version
     */
    private String extractNameFromURL(URL url, Map<String, String> labels) {
          String text = url.toString();
          int idx = text.lastIndexOf('!');
          if (idx > 0) {
              text = text.substring(0, idx);
          }
        String[] parts = text.split("/");
        if (parts != null && parts.length > 2) {
            String version = parts[parts.length - 2];
            String name = parts[parts.length - 3];
            labels.put("project", name);
            labels.put("version", version);
            return name;
        }
        return null;
    }

    private void populateLabelsFromResources(KubernetesResource resource, Map<String, String> labels) {
        if (resource instanceof KubernetesList) {
            KubernetesList list = (KubernetesList) resource;
            List<HasMetadata> items = list.getItems();
            if (items != null) {
                for (HasMetadata item : items) {
                    populateLabelsFromEntity(item, labels);
                }
            }
        } else if (resource instanceof HasMetadata) {
            HasMetadata entity = (HasMetadata) resource;
            populateLabelsFromEntity(entity, labels);
        }
    }

    private void populateLabelsFromEntity(HasMetadata item, Map<String, String> labels) {
        ObjectMeta metadata = item.getMetadata();
        if (metadata != null) {
            Map<String, String> itemLabels = metadata.getLabels();
            if (itemLabels != null && itemLabels.size() > 0) {
                for (Map.Entry<String, String> entry : itemLabels.entrySet()) {
                    String key = entry.getKey();
                    if (!labels.containsKey(key)) {
                        labels.put(key, entry.getValue());
                    }
                }
            }
        }
    }

    private void populateAnnotationsFromResources(KubernetesResource resource, Map<String, String> annotations) {
        if (resource instanceof KubernetesList) {
            KubernetesList list = (KubernetesList) resource;
            List<HasMetadata> items = list.getItems();
            if (items != null) {
                for (HasMetadata item : items) {
                    populateAnnotationsForEntity(item, annotations);
                }
            }
        } else if (resource instanceof HasMetadata) {
            HasMetadata entity = (HasMetadata) resource;
            populateAnnotationsForEntity(entity, annotations);
        }
    }

    private void populateAnnotationsForEntity(HasMetadata item, Map<String, String> labels) {
        ObjectMeta metadata = item.getMetadata();
        if (metadata != null) {
            Map<String, String> itemAnnotations = metadata.getAnnotations();
            if (itemAnnotations != null && itemAnnotations.size() > 0) {
                for (Map.Entry<String, String> entry : itemAnnotations.entrySet()) {
                    String key = entry.getKey();
                    if (!labels.containsKey(key) && copiedAnnotations.contains(key)) {
                        labels.put(key, entry.getValue());
                    }
                }
            }
        }
    }

    protected Map<URL, KubernetesResource> loadYamlResourcesOnClassPath(String resourcePath) throws MojoExecutionException {
        List<URL> resourceList = findResourcesOnClassPath(resourcePath);
        Map<URL, KubernetesResource> resourceMap = new HashMap<>();
        for (URL url : resourceList) {
            try (InputStream is = url.openStream()) {
                if (is != null) {
                    KubernetesResource resource;
                    try {
                        resource = KubernetesHelper.loadYaml(is, KubernetesResource.class);
                        resourceMap.put(url, resource);
                    } catch (IOException e) {
                        log.warn("Ignoring resource " + url + " as it could not be parsed: " + e, e);
                        continue;
                    }
                }
            } catch (IOException e) {
                log.warn("Ignoring resource " + url + " as it could not be opened: " + e, e);
                continue;
            }
        }
        return resourceMap;
    }

    protected List<URL> findResourcesOnClassPath(String resourcePath) throws MojoExecutionException {
        try {
            ClassLoader classLoader = ClassUtil.createProjectClassLoader(project, log);
            List<URL> resourceList = new ArrayList<>();
            Enumeration<URL> resources = classLoader.getResources(resourcePath);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                if (url != null) {
                    resourceList.add(url);
                }
            }
            return resourceList;
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to find " + resourcePath + " on the compile classpath: " + e, e);
        }
    }
}
