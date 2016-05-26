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

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.utils.Files;
import org.apache.maven.shared.utils.StringUtils;

import static io.fabric8.kubernetes.api.KubernetesHelper.defaultApiVersion;

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
     * @param resourceFiles files to add.
     * @return the list builder
     * @throws IOException
     */
    public static KubernetesListBuilder readResourceFragmentsFrom(String apiVersion, String apiExtensionsVersion, File[] resourceFiles) throws IOException {
        KubernetesListBuilder k8sBuilder = new KubernetesListBuilder();
        if (resourceFiles != null) {
            for (File file : resourceFiles) {
                HasMetadata resource = getKubernetesResource(apiVersion, apiExtensionsVersion, file);
                k8sBuilder.withItems(resource);
            }
        }
        return k8sBuilder;
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
     *@param file file to read, whose name must match {@link #FILENAME_PATTERN}.  @return map holding the fragment
     */
    public static HasMetadata getKubernetesResource(String defaultApiVersion, String apiExtensionsVersion, File file) throws IOException {
        Map<String,Object> fragment = readAndEnrichFragment(defaultApiVersion, apiExtensionsVersion, file);
        ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(fragment, HasMetadata.class);
    }

    public static void writeResourceDescriptor(KubernetesList kubernetesList, File target, ResourceFileType resourceFileType)
        throws IOException {
        ObjectMapper mapper = resourceFileType.getObjectMapper()
                                              .enable(SerializationFeature.INDENT_OUTPUT)
                                              .disable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS)
                                              .disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        String serialized = mapper.writeValueAsString(kubernetesList);
        Files.writeToFile(resourceFileType.addExtension(target), serialized, Charset.defaultCharset());
    }


    public static File[] listResourceFragments(File resourceDir) {
        final Pattern filenamePattern = Pattern.compile(FILENAME_PATTERN);
        return resourceDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return filenamePattern.matcher(name).matches();
            }
        });
    }


    // ========================================================================================================

    private final static Map<String,String> FILENAME_TO_KIND_MAPPER = new HashMap<>();

    static {
        String mapping[] =
            {
                "deployment", "Deployment",
                "service", "Service",
                "svc", "Service",
                "sa", "ServiceAccount",
                "rc", "ReplicationController",
                "rs", "ReplicaSet"
            };
        for (int i = 0; i < mapping.length; i+=2) {
            FILENAME_TO_KIND_MAPPER.put(mapping[i],mapping[i+1]);
        }
    }

    private static final String FILENAME_PATTERN = "^(.*?)(-([^-]+))?\\.(yaml|yml|json)$";

    // Read fragment and add default values
    private static Map<String, Object> readAndEnrichFragment(String defaultApiVersion, String apiExtensionsVersion, File file) throws IOException {
        Pattern pattern = Pattern.compile(FILENAME_PATTERN, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(file.getName());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                String.format("Resource file name '%s' does not match pattern <name>-<type>.(yaml|yml|json)", file.getName()));
        }
        String name = matcher.group(1);
        String type = matcher.group(3);
        String ext = matcher.group(4).toLowerCase();
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

        if (StringUtils.isNotBlank(name)) {
            addIfNotExistent(fragment, "name", name);
        }

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
        return mapper.readValue(file, typeRef);
    }

}
