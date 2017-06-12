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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.boot.env.PropertySourcesLoader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * Utility methods to access spring-boot resources.
 * TODO: remove unwanted methods after Roland reviews and okays logic
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
    public static Properties getApplicationProperties(MavenProject project, String activeProfiles) throws IOException {
        return getApplicationProperties(project, getActiveProfiles(activeProfiles));
    }


    /**
     * TODO: need to check with Roland to see if we can use this approach
     * A simple short method where we simply run a small spring boot application during build and load the environment
     * to get the property sources, then load the properties from
     *
     * @param activeProfiles - the active profiles to be used if {@code null}, its not passed to spring boot application run
     * @return Properties - the application environment properties that will be used during image building process
     */
    public static Properties runAndLoadPropertiesUsingEnv(List<String> activeProfiles) {

        SpringApplication sbBuilder;

        if (activeProfiles != null && activeProfiles.size() > 0) {
            sbBuilder = new SpringApplicationBuilder(DummySpringBootApplication.class)
                    .web(false)
                    .headless(true)
                    .bannerMode(Banner.Mode.OFF)
                    .profiles(activeProfiles.toArray(new String[activeProfiles.size()]))
                    .build();
        } else {
            sbBuilder = new SpringApplicationBuilder(DummySpringBootApplication.class)
                    .web(false)
                    .headless(true)
                    .bannerMode(Banner.Mode.OFF)
                    .build();
        }

        Properties applicationProperties = new Properties();
        try {
            ConfigurableApplicationContext ctx = sbBuilder.run();

            applicationProperties = new Properties();

            for (PropertySource propertySource : ctx.getEnvironment().getPropertySources()) {

                if (propertySource != null && propertySource instanceof MapPropertySource) {
                    applicationProperties.putAll(((MapPropertySource) propertySource).getSource());
                }
            }
        } catch (Exception e) {
            //swallow it ..
        }

        return applicationProperties;
    }

    /**
     * Returns the spring boot configuration (supports `application.properties` and `application.yml`)
     * or an empty properties object if not found
     */
    public static Properties getApplicationProperties(MavenProject project, List<String> activeProfiles)
            throws IOException {

        return runAndLoadPropertiesUsingEnv(activeProfiles);
        //Properties props = new Properties();
//        addApplicationProperties(project, props, null);
//
//        //If the profiles are available load the profile resources as well
//        if (activeProfiles != null && !activeProfiles.isEmpty()) {
//            for (String profile : activeProfiles) {
//                addApplicationProperties(project, props, profile);
//            }
//        }
        //return props;
    }

    /**
     * Returns the given properties file on the project classpath if found or an empty properties object if not
     */
    public static Properties getPropertiesFile(MavenProject project, String propertiesFileName) throws IOException {
        URLClassLoader compileClassLoader = MavenUtil.getCompileClassLoader(project);
        URL resource = compileClassLoader.findResource(propertiesFileName);
        return getPropertiesResource(resource, null);
    }

    /**
     * Returns the given properties resource on the project classpath if found or an empty properties object if not
     */
    @SuppressWarnings("unchecked")
    protected static Properties getPropertiesResource(URL resource, String profile) throws IOException {
        Properties properties = new Properties();
        if (resource != null) {
            PropertiesPropertySourceLoader propertySourceLoader = new PropertiesPropertySourceLoader();
            PropertySource<Map> propertySource = (PropertySource<Map>) propertySourceLoader.load(resource.getFile(),
                    new UrlResource(resource), profile);
            if (propertySource != null) {
                properties.putAll(propertySource.getSource());
            }
        }
        return properties;
    }

    /**
     * Returns a {@code Properties} representation of the given Yaml resource or an empty properties object if the resource is null
     */
    @SuppressWarnings("unchecked")
    protected static Properties getPropertiesFromYamlResource(URL resource, String profile) throws IOException {
        Properties properties = new Properties();
        if (resource != null) {
            YamlPropertySourceLoader yamlPropertySourceLoader = new YamlPropertySourceLoader();

            PropertySource<Map> propertySource = (PropertySource<Map>)
                    yamlPropertySourceLoader.load(resource.getFile(),
                            new UrlResource(resource), profile);

            if (propertySource != null) {
                properties.putAll(propertySource.getSource());
            }
        }
        return properties;
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
    public static String getSpringBootVersion(MavenProject mavenProject) {return MavenUtil.getDependencyVersion(mavenProject, SpringBootConfigurationHelper.SPRING_BOOT_GROUP_ID, SpringBootConfigurationHelper.SPRING_BOOT_ARTIFACT_ID);

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

    private static void runAndLoadEnv(String... profiles) {
        SpringApplication mvnSpringApplication =
                new SpringApplicationBuilder()
                        .profiles(profiles)
                        .build();
        mvnSpringApplication.setWebEnvironment(false);

    }

    /**
     * Method to add the application properties from spring boot profile resources and merge them as one
     *
     * @param project          - the maven project of the build
     * @param mergedProperties - the merged properties container
     * @param profile          - the profile to use when searching the spring boot resources
     */
    private static void addApplicationProperties(MavenProject project, Properties mergedProperties,
                                                 String profile) throws IOException {

        PropertySourcesLoader propertySourcesLoader = new PropertySourcesLoader();

        //Applications can override the config file name using this system property
        //Ref: https://docs.spring.io/spring-boot/docs/current/reference/html/howto-properties-and-configuration.html

        String configFileName = System.getProperty("spring.config.name", "application");

        Set<Resource> includesResources = new LinkedHashSet<>();

        if (profile == null) {
            scanForApplicationPropertySources(project, configFileName, includesResources, null);

        } else {
            scanForApplicationPropertySources(project, configFileName, includesResources, null);
        }


        Properties profileProperties = new Properties();

        for (Resource resource : includesResources) {
            MapPropertySource propertySource = (MapPropertySource) propertySourcesLoader.load(resource);
            if (propertySource != null) {
                profileProperties.putAll(propertySource.getSource());
            }
        }

        mergedProperties.putAll(profileProperties);
    }

    /**
     * A utility method to scan for spring boot application sources {@code *.properties,*.yaml,*.json,*.yml}
     *
     * @param mavenProject      - the maven project which houses the spring boot application
     * @param configFileName    - the configuration file name {@code spring.config.name} system property or defaults to application
     * @param includesResources - the collection to which the scanned resources will be added
     * @param profile           - the Spring profile
     * @throws IOException - any exception that might occur while scanning the resource
     */
    private static void scanForApplicationPropertySources(MavenProject mavenProject, String configFileName,
                                                          Set<Resource> includesResources,
                                                          String profile) throws IOException {

        URLClassLoader classLoader = MavenUtil.getCompileClassLoader(mavenProject);
        PathMatchingResourcePatternResolver scanner = new PathMatchingResourcePatternResolver(classLoader);
        String propertiesFilePattern;

        //PROPERTIES
        if (profile == null) {
            propertiesFilePattern = "**/" + configFileName + ".properties";
        } else {
            propertiesFilePattern = "**/" + configFileName + "-" + profile + ".properties";
        }

        //PROPERTIES
        Resource[] propertiesResources = scanner.getResources(propertiesFilePattern);

        if (propertiesResources != null) {
            includesResources.addAll(Arrays.asList(propertiesResources));
        }

        //YML
        if (profile == null) {
            propertiesFilePattern = "**/" + configFileName + ".yml";
        } else {
            propertiesFilePattern = "**/" + configFileName + "-" + profile + ".yml";
        }

        Resource[] ymlResources = scanner.getResources(propertiesFilePattern);
        if (ymlResources != null) {
            includesResources.addAll(Arrays.asList(ymlResources));
        }

        //YAML
        if (profile == null) {
            propertiesFilePattern = "**/" + configFileName + ".yaml";
        } else {
            propertiesFilePattern = "**/" + configFileName + "-" + profile + ".yaml";
        }

        Resource[] yamlResources =
                scanner.getResources(propertiesFilePattern);
        includesResources.addAll(Arrays.asList(yamlResources));

        if (yamlResources != null) {
            includesResources.addAll(Arrays.asList(ymlResources));
        }

        //JSON
        if (profile == null) {
            propertiesFilePattern = "**/" + configFileName + ".json";
        } else {
            propertiesFilePattern = "**/" + configFileName + "-" + profile + ".json";
        }

        Resource[] jsonResources =
                scanner.getResources(propertiesFilePattern);
        includesResources.addAll(Arrays.asList(yamlResources));

        if (jsonResources != null) {
            includesResources.addAll(Arrays.asList(jsonResources));
        }
    }

}
