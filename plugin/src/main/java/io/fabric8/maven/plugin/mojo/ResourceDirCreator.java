package io.fabric8.maven.plugin.mojo;

import java.io.File;
import org.apache.commons.lang3.StringUtils;

public class ResourceDirCreator {

    public static File getFinalResourceDir(File resourceDir, String environment) {
        if (resourceDir != null && StringUtils.isNotEmpty(environment)) {
            return new File(resourceDir, environment);
        }

        return resourceDir;
    }

}
