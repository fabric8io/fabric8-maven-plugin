package io.fabric8.maven.core.util;

import org.apache.commons.lang3.StringUtils;

/**
 * @author roland
 * @since 24.05.17
 */
public class EnvUtil {

     public static String getEnvVarOrSystemProperty(String varName, String defaultValue) {
        return getEnvVarOrSystemProperty(varName, varName, defaultValue);
     }

     public static String getEnvVarOrSystemProperty(String envVarName, String systemProperty, String defaultValue) {
         String ret = System.getenv(envVarName);
         if (StringUtils.isNotBlank(ret)){
             return ret;
         }
         return System.getProperty(systemProperty, defaultValue);
     }
}
