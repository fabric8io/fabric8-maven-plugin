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

package io.fabric8.maven.core.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.utils.Files;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.StringUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for handling Kubernetes resource descriptors
 *
 * @author roland
 * @since 02/05/16
 */
public class KubernetesResourceUtil {

    public static final String API_VERSION = "v1";
    public static final String API_EXTENSIONS_VERSION = "extensions/v1beta1";


    /**
     * Read all Kubernetes resource fragments from a directory and create a {@link KubernetesListBuilder} which
     * can be adapted later.
     *
     * @param apiVersion the api version to use
     * @param apiExtensionsVersion the extension version to use
     * @param defaultName the default name to use when none is given
     * @param appResourcesOnly if only resource with the defaultName should be returned ?
     * @param resourceFiles files to add.
     * @return the list builder
     * @throws IOException
     */
    public static KubernetesListBuilder readResourceFragmentsFrom(String apiVersion,
                                                                  String apiExtensionsVersion,
                                                                  String defaultName,
                                                                  File[] resourceFiles) throws IOException {
        KubernetesListBuilder builder = new KubernetesListBuilder();
        if (resourceFiles != null) {
            for (File file : resourceFiles) {
                HasMetadata resource = getResource(apiVersion, apiExtensionsVersion, file, defaultName);
                builder.addToItems(resource);
            }
        }
        return builder;
    }

    /**
     * Read a Kubernetes resource fragment and add meta information extracted from the filename
     * to the resource descriptor. I.e. the following elements are added if not provided in the fragment:
     *
     * <ul>
     *     <li>name - Name of the resource added to metadata</li>
     *     <li>kind - Resource's kind</li>
     *     <li>apiVersion - API version (given as parameter to this method)</li>
     * </ul>
     *
     *
     * @param defaultApiVersion the API version to add if not given.
     * @param apiExtensionsVersion the API version for extensions
     * @param file file to read, whose name must match {@link #FILENAME_PATTERN}.  @return map holding the fragment
     * @param appName resource name specifying resources belonging to this application
     */
    public static HasMetadata getResource(String defaultApiVersion, String apiExtensionsVersion,
                                          File file, String appName) throws IOException {
        Map<String,Object> fragment = readAndEnrichFragment(defaultApiVersion, apiExtensionsVersion, file, appName);
        ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(fragment, HasMetadata.class);
    }

    public static String toYaml(Object resource) throws JsonProcessingException {
        return serializeAsString(resource, ResourceFileType.yaml);
    }

    public static String toJson(Object resource) throws JsonProcessingException {
        return serializeAsString(resource, ResourceFileType.json);
    }

    public static File writeResource(Object resource, File target, ResourceFileType resourceFileType) throws IOException {
        String serialized = serializeAsString(resource, resourceFileType);
        File outputFile = resourceFileType.addExtension(target);
        Files.writeToFile(outputFile, serialized, Charset.defaultCharset());
        return outputFile;
    }

    private static String serializeAsString(Object resource, ResourceFileType resourceFileType) throws JsonProcessingException {
        ObjectMapper mapper = resourceFileType.getObjectMapper()
                                              .enable(SerializationFeature.INDENT_OUTPUT)
                                              .disable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS)
                                              .disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        return mapper.writeValueAsString(resource);
    }

    public static File[] listResourceFragments(File resourceDir) {
        final Pattern filenamePattern = Pattern.compile(FILENAME_PATTERN);
        final Pattern exludePattern = Pattern.compile(PROFILES_PATTERN);
        return resourceDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return filenamePattern.matcher(name).matches() && !exludePattern.matcher(name).matches();
            }
        });
    }


    // ========================================================================================================

    private final static Map<String,String> FILENAME_TO_KIND_MAPPER = new HashMap<>();
    private final static Map<String,String> KIND_TO_FILENAME_MAPPER = new HashMap<>();
    private static String mappings[] =
        {
            // lets put the abbreviation we want to use first
            "cm", "ConfigMap",
            "configmap", "ConfigMap",
            "deployment", "Deployment",
            "ns", "Namespace",
            "namespace", "Namespace",
            "oauthclient", "OAuthClient",
            "pv", "PersistentVolume",
            "pvc", "PersistentVolumeClaim",
            "project", "Project",
            "pr", "ProjectRequest",
            "rb", "RoleBinding",
            "rolebinding", "RoleBinding",
            "secret", "Secret",
            "service", "Service",
            "svc", "Service",
            "sa", "ServiceAccount",
            "rc", "ReplicationController",
            "rs", "ReplicaSet",

            // OpenShift Resources:
            "route", "Route",
            "dc", "DeploymentConfig",
            "deploymentconfig", "DeploymentConfig",
            "template", "Template",
        };

    static {
        for (int i = 0; i < mappings.length; i+=2) {
            FILENAME_TO_KIND_MAPPER.put(mappings[i], mappings[i+1]);
            KIND_TO_FILENAME_MAPPER.put(mappings[i+1], mappings[i]);
        }
    }

    private static final String FILENAME_PATTERN = "^(?<name>.*?)(-(?<type>[^-]+))?\\.(?<ext>yaml|yml|json)$";
    private static final String PROFILES_PATTERN = "^profiles?\\.ya?ml$";

    // Read fragment and add default values
    private static Map<String, Object> readAndEnrichFragment(String defaultApiVersion, String apiExtensionsVersion,
                                                             File file, String appName) throws IOException {
        Pattern pattern = Pattern.compile(FILENAME_PATTERN, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(file.getName());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                String.format("Resource file name '%s' does not match pattern <name>-<type>.(yaml|yml|json)", file.getName()));
        }
        String name = matcher.group("name");
        String type = matcher.group("type");
        String ext = matcher.group("ext").toLowerCase();
        String kind;

        Map<String,Object> fragment = readFragment(file, ext);

        if (type != null) {
            kind = getAndValidateKindFromType(file, type);
        } else {
            // Try name as type
            kind = FILENAME_TO_KIND_MAPPER.get(name.toLowerCase());
            if (kind != null) {
                // Name is in fact the type, so lets erase the name.
                name = null;
            }
        }

        addKind(fragment, kind, file.getName());

        String apiVersion = defaultApiVersion;
        if (Objects.equals(kind, "Deployment") || Objects.equals(kind, "Ingress")) {
            apiVersion = apiExtensionsVersion;
        }
        addIfNotExistent(fragment, "apiVersion", apiVersion);

        Map<String, Object> metaMap = getMetadata(fragment);
        // No name means: generated app name should be taken as resource name
        addIfNotExistent(metaMap, "name", StringUtils.isNotBlank(name) ? name : appName);

        addIfNotExistent(fragment, "apiVersion", defaultApiVersion);
        return fragment;
    }

    private static String getAndValidateKindFromType(File file, String type) {
        String kind;
        kind = FILENAME_TO_KIND_MAPPER.get(type.toLowerCase());
        if (kind == null) {
            throw new IllegalArgumentException(
                String.format("Unknown type '%s' for file %s. Must be one of : %s",
                              type, file.getName(), StringUtils.join(FILENAME_TO_KIND_MAPPER.keySet().iterator(), ", ")));
        }
        return kind;
    }

    private static void addKind(Map<String, Object> fragment, String kind, String fileName) {
        if (kind == null && !fragment.containsKey("kind")) {
            throw new IllegalArgumentException(
                "No type given as part of the file name (e.g. 'app-rc.yml') " +
                "and no 'Kind' defined in resource descriptor " + fileName);
        }
        addIfNotExistent(fragment, "kind", kind);
    }

    // ===============================================================================================

    private static Map<String, Object> getMetadata(Map<String, Object> fragment) {
        Map<String, Object> meta = (Map<String, Object>) fragment.get("metadata");
        if (meta == null) {
            meta = new HashMap<>();
            fragment.put("metadata", meta);
        }
        return meta;
    }

    private static void addIfNotExistent(Map<String, Object> fragment, String key, String value) {
        if (!fragment.containsKey(key)) {
            fragment.put(key, value);
        }
    }

    private static Map<String,Object> readFragment(File file, String ext) throws IOException {
        ObjectMapper mapper = new ObjectMapper("json".equals(ext) ? new JsonFactory() : new YAMLFactory());
        TypeReference<HashMap<String,Object>> typeRef = new TypeReference<HashMap<String,Object>>() {};
        try {
            return mapper.readValue(file, typeRef);
        } catch (JsonProcessingException e) {
            // TODO is there a cleaner way to associate the file information in the exception message?
            throw new JsonMappingException("file: " + file + ". " + e.getMessage(), e.getLocation(), e);
        }
    }

    public static String getNameWithSuffix(String name, String kind) {
        String suffix =  KIND_TO_FILENAME_MAPPER.get(kind);
        return suffix != null ? name +  "-" + suffix : name;
    }

    public static String extractContainerName(MavenProject project, ImageConfiguration imageConfig) {
        String alias = imageConfig.getAlias();
        return alias != null ? alias : extractImageUser(imageConfig.getName(), project) + "-" + project.getArtifactId();
    }

    private static String extractImageUser(String image, MavenProject project) {
        ImageName name = new ImageName(image);
        String imageUser = name.getUser();
        return imageUser != null ? imageUser : project.getGroupId();
    }

    public static Map<String, String> removeVersionSelector(Map<String, String> selector) {
        Map<String, String> answer = new HashMap<>(selector);
        answer.remove("version");
        return answer;
    }

    public static boolean checkForKind(KubernetesListBuilder builder, String... kinds) {
        Set<String> kindSet = new HashSet<>(Arrays.asList(kinds));
        for (HasMetadata item : builder.getItems()) {
            if (kindSet.contains(item.getKind())) {
                return true;
            }
        }
        return false;
    }
}
