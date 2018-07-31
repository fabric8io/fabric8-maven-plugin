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
package io.fabric8.maven.plugin.mojo.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.Annotations;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.maven.plugin.mojo.AbstractFabric8Mojo;
import io.fabric8.openshift.api.model.Template;
import io.fabric8.utils.Function;
import io.fabric8.utils.IOHelpers;
import io.fabric8.utils.URLUtils;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.fabric8.kubernetes.api.KubernetesHelper.getOrCreateAnnotations;
import static io.fabric8.utils.Strings.isNotBlank;

/**
 */
public abstract class AbstractArtifactSearchMojo extends AbstractFabric8Mojo {
    @Parameter(defaultValue = "${project.remoteArtifactRepositories}")
    protected List<MavenArtifactRepository> remoteRepositories;
    @Component
    protected ArtifactResolver artifactResolver;
    @Parameter(property = "fabric8.repository.index.mavenRepoUrl", defaultValue = "http://central.maven.org/maven2/")
    protected String mavenRepoUrl;
    @Parameter(property = "fabric8.repository.index.mavenRepoSearchUrl", defaultValue = "http://search.maven.org/solrsearch/select")
    protected String mavenRepoSearchUrl;
    @Parameter(property = "fabric8.repository.index.maxSearchResults", defaultValue = "200000")
    protected int maxSearchResults;
    @Parameter(defaultValue = "${repositorySystemSession}")
    private RepositorySystemSession repoSession;
    @Parameter(name = "iconMappings")
    private List<Mapping> iconMappings;
    private Map<String, String> iconMappingsMap;

    protected static String findManifestAnnotation(Object manifest, final String annotation) {
        return findManifestValue(manifest, new Function<HasMetadata, String>() {
            @Override
            public String apply(HasMetadata hasMetadata) {
                Map<String, String> annotations = getOrCreateAnnotations(hasMetadata);
                String answer = annotations.get(annotation);
                if (isNotBlank(answer)) {
                    return answer;
                }
                return null;
            }
        });
    }

    protected static String findManifestIcon(Object manifest) {
        return findManifestAnnotation(manifest, Annotations.Builds.ICON_URL);
    }

    protected static <T> T findManifestValue(Object manifest, Function<HasMetadata, T> function) {
        if (manifest instanceof HasMetadata) {
            HasMetadata metadata = (HasMetadata) manifest;
            T answer = function.apply(metadata);
            if (answer != null) return answer;
        }
        if (manifest instanceof KubernetesList) {
            KubernetesList list = (KubernetesList) manifest;
            return findManifestValueFromList(list.getItems(), function);
        }
        if (manifest instanceof Template) {
            Template template = (Template) manifest;
            return findManifestValueFromList(template.getObjects(), function);
        }
        return null;

    }

    private static <T> T findManifestValueFromList(List<HasMetadata> items, Function<HasMetadata, T> function) {
        if (items != null) {
            for (HasMetadata item : items) {
                T answer = findManifestValue(item, function);
                if (answer != null) {
                    return answer;
                }
            }
        }
        return null;
    }

    protected static String getHtmlFileContentOrDefault(File htmlFile, String defaultValue) throws MojoExecutionException {
        if (htmlFile != null && htmlFile.isFile()) {
            try {
                return IOHelpers.readFully(htmlFile);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to load HTML: " + htmlFile + ". " + e, e);
            }
        } else {
            return defaultValue;
        }
    }

    public Map<String, String> getIconMappingsMap() {
        if (iconMappingsMap == null) {
            iconMappingsMap = new HashMap<>();

            if (iconMappings != null) {
                for (Mapping mapping : iconMappings) {
                    iconMappingsMap.put(mapping.getKey(), mapping.getValue());
                }
            }
            getLog().debug("Loaded icon mappings: " + iconMappingsMap);
        }
        return iconMappingsMap;
    }

    public String convertRelativeIcon(String icon) {
        if (isNotBlank(icon)) {
            String answer = getIconMappingsMap().get(icon);
            if (answer != null) {
                return answer;
            }

            // lets ignore empty data icons
            if (icon.equals("data:image/svg+xml;charset=UTF-8;base64,")) {
                return null;
            }

            // lets switch icon host as SVG icons don't work there due to content type or something...
            String prefix = "https://raw.githubusercontent.com/";
            if (icon.startsWith(prefix)) {
                return URLUtils.pathJoin("https://cdn.rawgit.com/", icon.substring(prefix.length()));
            }
            if (icon.indexOf(':') < 0) {
                // probably a relative icon
                icon = URLUtils.pathJoin("https://cdn.rawgit.com/fabric8io/fabric8-console/master/", icon);
            }
        }
        return icon;
    }

    protected List<HelmIndexMojo.ArtifactDTO> searchMaven(String query) throws MojoExecutionException {
        String urlText = mavenRepoSearchUrl + query + "&wt=json&rows=" + maxSearchResults;
        HelmIndexMojo.Result result = null;
        try {
            URL url = new URL(urlText);
            ObjectMapper mapper = new ObjectMapper();
            result = mapper.readerFor(HelmIndexMojo.Result.class).readValue(url);
        } catch (IOException e) {
            throw new MojoExecutionException("Could not query " + urlText + " : " + e, e);
        }

        if (result == null) {
            throw new MojoExecutionException("No result!");
        }
        HelmIndexMojo.Response response = result.getResponse();
        if (response == null) {
            throw new MojoExecutionException("No response!");
        }
        List<HelmIndexMojo.ArtifactDTO> artifacts = response.getDocs();
        if (artifacts == null) {
            throw new MojoExecutionException("No docs!");
        }
        return artifacts;
    }

    protected File resolveArtifactFile(HelmIndexMojo.ArtifactDTO artifactDTO, String classifier, String extension) {
        File file = null;
        try {
            ArtifactRequest artifactRequest = new ArtifactRequest();
            org.eclipse.aether.artifact.Artifact artifact = new DefaultArtifact(artifactDTO.getG(), artifactDTO.getA(), classifier, extension, artifactDTO.getV());
            artifactRequest.setArtifact(artifact);

            // convert maven remote repositories to Aether repos
            List<RemoteRepository> aetherRepoList = new ArrayList<>();
            for (MavenArtifactRepository remoteRepository : remoteRepositories) {
                RemoteRepository.Builder builder = new RemoteRepository.Builder(remoteRepository.getId(), remoteRepository.getLayout().getId(), remoteRepository.getUrl());
                RemoteRepository aetherRepo = builder.build();
                aetherRepoList.add(aetherRepo);
            }
            artifactRequest.setRepositories(aetherRepoList);

            ArtifactResult artifactResult = artifactResolver.resolveArtifact(repoSession, artifactRequest);
            org.eclipse.aether.artifact.Artifact resolvedArtifact = artifactResult.getArtifact();
            if (resolvedArtifact == null) {
                getLog().warn("Could not resolve artifact " + artifactDTO.description());
                return null;
            }
            file = resolvedArtifact.getFile();

        } catch (Exception e) {
            getLog().warn("Failed to resolve manifest manifest for " + artifactDTO.description() + ". " + e, e);
            return null;
        }

        if (file == null) {
            getLog().warn("Could not resolve artifact file for " + artifactDTO.description());
            return null;
        }
        if (!file.isFile() || !file.exists()) {
            getLog().warn("Resolved artifact file does not exist for " + artifactDTO.description());
            return null;
        }
        return file;
    }

    protected Object loadKubernetesManifestFile(ArtifactDTO artifactDTO) {
        return loadManifestFile(artifactDTO, "kubernetes", "yml");
    }

    protected Object loadOpenShiftManifestFile(ArtifactDTO artifactDTO) {
        return loadManifestFile(artifactDTO, "openshift", "yml");
    }

    private Object loadManifestFile(ArtifactDTO artifactDTO, String classifier, String extension) {
        File file = resolveArtifactFile(artifactDTO, classifier, extension);
        if (file == null) return null;
        if (!file.isFile() || !file.exists()) {
            getLog().warn("No YAML manifest exists at " + file);
            return null;
        }
        try {
            return KubernetesHelper.loadYaml(file);
        } catch (IOException e) {
            getLog().warn("Failed to parse " + file + ". " + e, e);
            return null;
        }
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private Response response;

        public Response getResponse() {
            return response;
        }

        public void setResponse(Response response) {
            this.response = response;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {
        private List<ArtifactDTO> docs;

        public List<ArtifactDTO> getDocs() {
            return docs;
        }

        public void setDocs(List<ArtifactDTO> docs) {
            this.docs = docs;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ArtifactDTO {
        private String id;
        private String g;
        private String a;
        private String v;
        private String p;
        private List<String> ec;
        private List<String> tags;

        @Override
        public String toString() {
            return "ArtifactDTO: " + g + ":" + a + ":" + v;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getG() {
            return g;
        }

        public void setG(String g) {
            this.g = g;
        }

        public String getA() {
            return a;
        }

        public void setA(String a) {
            this.a = a;
        }

        public String getV() {
            return v;
        }

        public void setV(String v) {
            this.v = v;
        }

        public String getP() {
            return p;
        }

        public void setP(String p) {
            this.p = p;
        }

        public List<String> getEc() {
            return ec;
        }

        public void setEc(List<String> ec) {
            this.ec = ec;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public String description() {
            return "" + g + ":" + a + ":" + v;
        }

        public String createKey() {
            return getA() + "-" + getV();
        }
    }

    public static class Mapping {
        private String key;
        private String value;

        @Override
        public String toString() {
            return "Mapping{" +
                    "key='" + key + '\'' +
                    ", value='" + value + '\'' +
                    '}';
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
