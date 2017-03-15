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

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * Utility methods to access spring-boot resources.
 */
public class SpringBootUtil {

    private static final transient Logger LOG = LoggerFactory.getLogger(SpringBootUtil.class);

    /**
     * This method computes the active profiles and delegate the call to the getApplicationProperties method
     *
     * @param project        - the {@link MavenProject}
     * @param activeProfiles - the comma separated String of profiles
     * @return properties - the merged properties of all profiles
     */
    public static Properties getApplicationProperties(MavenProject project, String activeProfiles) {
        return getApplicationProperties(project, getActiveProfiles(activeProfiles));
    }

    /**
     * Returns the spring boot configuration (supports `application.properties` and `application.yml`)
     * or an empty properties object if not found
     */
    public static Properties getApplicationProperties(MavenProject project, List<String> activeProfiles) {
        URLClassLoader compileClassLoader = MavenUtil.getCompileClassLoader(project);

        Properties props = new Properties();
        addApplicationProperties(activeProfiles, compileClassLoader, props, null);

        //If the profiles are available load the profile resources as well
        if (activeProfiles != null) {
            for (String profile : activeProfiles) {
                addApplicationProperties(activeProfiles, compileClassLoader, props, profile);
            }
        }
        return props;
    }

    /**
     * Returns the given properties file on the project classpath if found or an empty properties object if not
     */
    public static Properties getPropertiesFile(MavenProject project, String propertiesFileName) {
        URLClassLoader compileClassLoader = MavenUtil.getCompileClassLoader(project);
        URL resource = compileClassLoader.findResource(propertiesFileName);
        return getPropertiesResource(resource);
    }

    /**
     * Returns the given properties resource on the project classpath if found or an empty properties object if not
     */
    protected static Properties getPropertiesResource(URL resource) {
        Properties answer = new Properties();
        if (resource != null) {
            try (InputStream stream = resource.openStream()) {
                answer.load(stream);
            } catch (IOException e) {
                throw new IllegalStateException("Error while reading resource from URL " + resource, e);
            }
        }
        return answer;
    }

    /**
     * Returns a {@code Properties} representation of the given Yaml file on the project classpath if found or an empty properties object if not
     */
    public static Properties getPropertiesFromYamlFile(MavenProject project, String yamlFileName,
                                                       List<String> activeProfiles) {
        URLClassLoader compileClassLoader = MavenUtil.getCompileClassLoader(project);
        URL resource = compileClassLoader.findResource(yamlFileName);
        return getPropertiesFromYamlResource(resource, activeProfiles);
    }

    /**
     * Returns a {@code Properties} representation of the given Yaml resource or an empty properties object if the resource is null
     */
    protected static Properties getPropertiesFromYamlResource(URL resource, List<String> activeProfiles) {
        if (resource != null) {
            try (InputStream yamlStream = resource.openStream()) {
                Yaml yaml = new Yaml(new SafeConstructor());

                Map<String, Map> profileDocs = new HashMap<>();

                Properties properties = new Properties();

                Iterable<Object> yamlDoc = yaml.loadAll(yamlStream);

                Iterator yamlDocIterator = yamlDoc.iterator();

                int docCount = 0;
                while (yamlDocIterator.hasNext()) {
                    Map docRoot = (Map) yamlDocIterator.next();

                    String profiles = null;

                    if (docRoot.containsKey("spring")) {

                        LinkedHashMap value = (LinkedHashMap) docRoot.get("spring");

                        Object profilesValue = value.get("profiles");

                        if (profilesValue instanceof Map) {
                            Map profileMap = (Map) profilesValue;
                            if (activeProfiles.isEmpty() && docCount > 0) {
                                if (profileMap.containsKey("active")) {
                                    activeProfiles.addAll(getActiveProfiles((String) profileMap.get("active")));
                                }
                            }
                        } else if (profilesValue instanceof String) {
                            profiles = (String) profilesValue;
                        }
                    }

                    if (profiles != null) {
                        String[] profileSplit = profiles.split("\\s*,\\s*");
                        if (!CollectionUtils.
                                intersection(Arrays.asList(profileSplit), activeProfiles)
                                .isEmpty()) {
                            //if the profiles is in the list of active profiles we add it to our list of docs
                            profileDocs.put(profiles, docRoot);
                        }
                    } else if (docCount == 0) {
                        //the root doc
                        profileDocs.put("default", docRoot);
                    }

                    docCount++;
                }

                LOG.debug("Spring Boot Profile docs:{}" + profileDocs);

                properties.putAll(getFlattenedMap(profileDocs.get("default")));

                for (String activeProfile : activeProfiles) {
                    if (profileDocs.containsKey(activeProfile)) {
                        properties.putAll(getFlattenedMap(profileDocs.get(activeProfile)));
                    }
                }

                return properties;
            } catch (IOException e) {
                throw new IllegalStateException("Error while reading Yaml resource from URL " + resource, e);
            }
        }
        return new Properties();
    }

    /**
     * Determine the spring-boot devtools version for the current project
     */
    public static String getSpringBootDevToolsVersion(MavenProject mavenProject) {
        return getSpringBootVersion(mavenProject);
    }

    /**
     * Determine the spring-boot major version for the current project
     */
    public static String getSpringBootVersion(MavenProject mavenProject) {
        return MavenUtil.getDependencyVersion(mavenProject, SpringBootConfigurationHelper.SPRING_BOOT_GROUP_ID, SpringBootConfigurationHelper.SPRING_BOOT_ARTIFACT_ID);
    }

    /**
     * Build a flattened representation of the Yaml tree. The conversion is compliant with the spring-boot rules.
     */
    private static Map<String, Object> getFlattenedMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        buildFlattenedMap(result, source, null);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void buildFlattenedMap(Map<String, Object> result, Map<String, Object> source, String path) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if (path != null && path.trim().length() > 0) {
                if (key.startsWith("[")) {
                    key = path + key;
                } else {
                    key = path + "." + key;
                }
            }
            Object value = entry.getValue();
            if (value instanceof String) {
                result.put(key, value);
            } else if (value instanceof Map) {

                Map<String, Object> map = (Map<String, Object>) value;
                buildFlattenedMap(result, map, key);
            } else if (value instanceof Collection) {
                Collection<Object> collection = (Collection<Object>) value;
                int count = 0;
                for (Object object : collection) {
                    buildFlattenedMap(result,
                            Collections.singletonMap("[" + (count++) + "]", object), key);
                }
            } else {
                result.put(key, (value != null ? value : ""));
            }
        }
    }

    public static List<String> getActiveProfiles(String strActiveProfiles) {
        List<String> activeProfiles = new ArrayList<>();
        if (strActiveProfiles != null) {
            activeProfiles = Arrays.asList(strActiveProfiles.split("\\s*,\\s*"));
        }
        return activeProfiles;
    }


    /**
     * Utility method to find classpath resource
     *
     * @param compileClassLoader - the classloader to search resource for
     * @param resourceName       - the name of the resource
     * @return URL of the resource
     */
    private static URL getResourceFromClasspath(URLClassLoader compileClassLoader, String resourceName) {
        URL urlResource = compileClassLoader.findResource(resourceName);
        return urlResource;
    }

    /**
     * Method to add the application properties from spring boot profile resources and merge them as one
     *
     * @param activeProfiles     - the active profiles list typically a comma separated string of profile names
     * @param compileClassLoader - the classloader in which the resource will be searched
     * @param mergedProperties              - the merged properties container
     * @param profile            - the profile to use when searching the spring boot resources
     */
    private static void addApplicationProperties(List<String> activeProfiles, URLClassLoader compileClassLoader,
                                                 Properties mergedProperties, String profile) {
        URL ymlResource;
        URL propertiesResource;
        Properties profileProperties;

        if (profile == null) {
            ymlResource = compileClassLoader.findResource("application.yml");
            propertiesResource = compileClassLoader.findResource("application.properties");
            mergedProperties = getPropertiesFromYamlResource(ymlResource, activeProfiles);
            mergedProperties.putAll(getPropertiesResource(propertiesResource));
        } else {
            ymlResource = compileClassLoader.findResource("application-" + profile + ".yml");
            profileProperties = getPropertiesFromYamlResource(ymlResource, activeProfiles);
            propertiesResource = getResourceFromClasspath(compileClassLoader,
                    "application-" + profile + ".properties");
            profileProperties.putAll(getPropertiesResource(propertiesResource));
            mergedProperties.putAll(profileProperties);
        }
    }

}
