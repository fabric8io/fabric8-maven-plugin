package io.fabric8.maven.core.util;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;

public class ThorntailUtil {

    /**
     * Returns the thorntail configuration (supports `project-defaults.yml`)
     * or an empty properties object if not found
     */
    public static Properties getThorntailProperties(URLClassLoader compileClassLoader) {
        URL ymlResource = compileClassLoader.findResource("project-defaults.yml");

        Properties props = YamlUtil.getPropertiesFromYamlResource(ymlResource);
        return props;
    }
}
