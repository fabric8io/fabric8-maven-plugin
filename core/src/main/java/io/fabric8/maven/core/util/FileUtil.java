package io.fabric8.maven.core.util;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * File related methods which cannot be found elsewhere
 * @author roland
 * @since 23.05.17
 */
public class FileUtil {

    public static File getRelativePath(File baseDir, File file) {
        Path baseDirPath = Paths.get(baseDir.getAbsolutePath());
        Path filePath = Paths.get(file.getAbsolutePath());
        return baseDirPath.relativize(filePath).toFile();
    }

    public static String stripPrefix(String text, String prefix) {
        if (text.startsWith(prefix)) {
            return text.substring(prefix.length());
        }
        return text;
    }

    public static String stripPostfix(String text, String postfix) {
        if (text.endsWith(postfix)) {
            return text.substring(text.length() - postfix.length());
        }
        return text;
    }


   /**
     * Returns the absolute path to a file with name <code>fileName</code>
     * @param fileName the name of a file
     * @return absolute path to the file
     */
    public static String getAbsolutePath(String fileName) {
        return Paths.get(fileName).toAbsolutePath().toString();
    }

    /**
     * Returns the absolute path to a resource addressed by the given <code>url</code>
     * @param url resource URL
     * @return absolute path to the resource
     */
    public static String getAbsolutePath(URL url) {
        try {
            return url != null ? Paths.get(url.toURI()).toAbsolutePath().toString() : null;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
