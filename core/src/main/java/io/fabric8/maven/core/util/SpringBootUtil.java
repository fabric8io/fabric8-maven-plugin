package io.fabric8.maven.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;

import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * Utility methods to access spring-boot resources.
 */
public class SpringBootUtil {

    private static final transient Logger LOG = LoggerFactory.getLogger(SpringBootUtil.class);

    /**
     * Returns the spring boot configuration (supports `application.properties` and `application.yml`)
     * or an empty properties object if not found
     */
    public static Properties getSpringBootApplicationProperties(MavenProject project) {
        URLClassLoader compileClassLoader = MavenUtil.getCompileClassLoader(project);
        URL ymlResource = compileClassLoader.findResource("application.yml");
        URL propertiesResource = compileClassLoader.findResource("application.properties");

        Properties props = getPropertiesFromYamlResource(ymlResource);
        props.putAll(getPropertiesResource(propertiesResource));
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
            try {
                answer.load(resource.openStream());
            } catch (IOException e) {
                LOG.error("Failed to load properties from: " + resource + ". " + e, e);
            }
        }
        return answer;
    }

    /**
     * Returns a {@code Properties} representation of the given Yaml file on the project classpath if found or an empty properties object if not
     */
    public static Properties getPropertiesFromYamlFile(MavenProject project, String yamlFileName) {
        URLClassLoader compileClassLoader = MavenUtil.getCompileClassLoader(project);
        URL resource = compileClassLoader.findResource(yamlFileName);
        return getPropertiesFromYamlResource(resource);
    }

    /**
     * Returns a {@code Properties} representation of the given Yaml resource or an empty properties object if the resource is null
     */
    protected static Properties getPropertiesFromYamlResource(URL resource) {
        if (resource != null) {
            try (InputStream yamlStream = resource.openStream()) {
                Yaml yaml = new Yaml();
                @SuppressWarnings("unchecked")
                SortedMap<String, Object> source = yaml.loadAs(yamlStream, SortedMap.class);
                Properties properties = new Properties();
                properties.putAll(getFlattenedMap(source));
                return properties;
            } catch (Exception e) {
                LOG.error("Failed to load yaml properties from: " + resource + ". " + e, e);
            }
        }
        return new Properties();
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
            if (path !=null && path.trim().length()>0) {
                if (key.startsWith("[")) {
                    key = path + key;
                }
                else {
                    key = path + "." + key;
                }
            }
            Object value = entry.getValue();
            if (value instanceof String) {
                result.put(key, value);
            }
            else if (value instanceof Map) {

                Map<String, Object> map = (Map<String, Object>) value;
                buildFlattenedMap(result, map, key);
            }
            else if (value instanceof Collection) {
                Collection<Object> collection = (Collection<Object>) value;
                int count = 0;
                for (Object object : collection) {
                    buildFlattenedMap(result,
                            Collections.singletonMap("[" + (count++) + "]", object), key);
                }
            }
            else {
                result.put(key, (value != null ? value : ""));
            }
        }
    }

}
