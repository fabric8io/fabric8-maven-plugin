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

package io.fabric8.maven.plugin.combine;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

/**
 * @author roland
 * @since 31/03/16
 */
public class JsonCombiner {

    private final Log log;
    private MavenProject project;

    public JsonCombiner(Log log, MavenProject project) {
        this.project = project;
        this.log = log;
    }

    /*
    public void combineDependentJsonFiles(File json, String templateName) throws MojoExecutionException {
        try {
            Set<File> jsonFiles = new LinkedHashSet<>();
            Set<Artifact> dependencyArtifacts = project.getDependencyArtifacts();
            for (Artifact artifact : dependencyArtifacts) {
                String classifier = artifact.getClassifier();
                String type = artifact.getType();
                File file = artifact.getFile();

                if (MavenUtils.isKubernetesJsonArtifact(classifier, type)) {
                    if (file != null) {
                        System.out.println("Found kubernetes JSON dependency: " + artifact);
                        jsonFiles.add(file);
                    } else {
                        Set<Artifact> artifacts = resolveArtifacts(artifact);
                        for (Artifact resolvedArtifact : artifacts) {
                            classifier = resolvedArtifact.getClassifier();
                            type = resolvedArtifact.getType();
                            file = resolvedArtifact.getFile();
                            if (MavenUtils.isKubernetesJsonArtifact(classifier, type) && file != null) {
                                System.out.println("Resolved kubernetes JSON dependency: " + artifact);
                                jsonFiles.add(file);
                            }
                        }
                    }
                }
            }
            List<Object> jsonObjectList = new ArrayList<>();
            for (File file : jsonFiles) {
                addKubernetesJsonFileToList(jsonObjectList, file);
            }
            if (jsonObjectList.isEmpty()) {
                throw new MojoExecutionException("Could not find any dependent kubernetes JSON files!");
            }
            Object combinedJson;
            if (jsonObjectList.size() == 1) {
                combinedJson = jsonObjectList.get(0);
            } else {
                combinedJson = KubernetesHelper.combineJson(jsonObjectList.toArray());
            }
            if (combinedJson instanceof Template) {
                Template template = (Template) combinedJson;
                KubernetesHelper.setName(template, templateName);
                configureTemplateDescriptionAndIcon(template, getIconUrl());

                addLabelIntoObjects(template.getObjects(), "package", templateName);

                if (pureKubernetes) {
                    combinedJson = applyTemplates(template);
                }
            }
            if (pureKubernetes) {
                combinedJson = filterPureKubernetes(combinedJson);
            }
            json.getParentFile().mkdirs();
            KubernetesHelper.saveJson(json, combinedJson);
            log.info("Saved as :" + json.getAbsolutePath());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to save combined JSON files " + json + " and " + extraJson + " as " + json + ". " + e, e);
        }
    }

    protected void addLabelIntoObjects(List<HasMetadata> objects, String label, String value) {
        for (HasMetadata object : objects) {
            addLabelIfNotExist(object, label, value);
            if (object instanceof ReplicationController) {
                ReplicationController entity = (ReplicationController) object;
                ReplicationControllerSpec spec = entity.getSpec();
                if (spec != null) {
                    final PodTemplateSpec template = spec.getTemplate();
                    if (template != null) {
                        // TODO hack until this is fixed https://github.com/fabric8io/kubernetes-model/issues/112
                        HasMetadata hasMetadata = new HasMetadata() {
                            @Override
                            public ObjectMeta getMetadata() {
                                return template.getMetadata();
                            }

                            @Override
                            public void setMetadata(ObjectMeta objectMeta) {
                                template.setMetadata(objectMeta);
                            }

                            @Override
                            public String getKind() {
                                return "PodTemplateSpec";
                            }
                        };
                        addLabelIfNotExist(hasMetadata, label, value);
                    }
                }
            }
        }
    }


    protected boolean addLabelIfNotExist(HasMetadata object, String label, String value) {
        if (object != null) {
            Map<String, String> labels = KubernetesHelper.getOrCreateLabels(object);
            if (labels.get(label) == null) {
                labels.put(label, value);
                return true;
            }
        }
        return false;
    }

    private void addKubernetesJsonFileToList(List<Object> list, File file) {
        if (file.exists() && file.isFile()) {
            try {
                Object jsonObject = loadJsonFile(file);
                if (jsonObject != null) {
                    list.add(jsonObject);
                } else {
                    log.warn("No object found for file: " + file);
                }
            } catch (MojoExecutionException e) {
                log.warn("Failed to parse file " + file + ". " + e, e);
            }
        } else {
            log.warn("Ignoring missing file " + file);
        }
    }

    protected Set<Artifact> resolveArtifacts(Artifact artifact) {
        ArtifactResolutionRequest request = new ArtifactResolutionRequest();
        request.setArtifact(artifact);
        request.setRemoteRepositories(remoteRepositories);
        request.setLocalRepository(localRepository);

        ArtifactResolutionResult resolve = resolver.resolve(request);
        return resolve.getArtifacts();
    }

    public void combineJsonFiles(File target, File kubernetesExtraJson) throws MojoExecutionException {
        // lets combine json files together
        log.info("Combining generated json " + target + " with extra json " + kubernetesExtraJson);
        Object extra = loadJsonFile(kubernetesExtraJson);
        Object generated = loadJsonFile(target);
        try {
            Object combinedJson = KubernetesHelper.combineJson(generated, extra);
            KubernetesHelper.saveJson(target, combinedJson);
            log.info("Saved as :" + target.getAbsolutePath());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to save combined JSON files " + target + " and " + kubernetesExtraJson + " as " + target + ". " + e, e);
        }
    }
*/
}
