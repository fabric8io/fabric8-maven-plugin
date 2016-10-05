package io.fabric8.maven.generator.webapp.handler;

import io.fabric8.maven.generator.webapp.AppServerHandler;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.DirectoryScanner;

/**
 * @author kameshs
 */
public abstract class AbstractAppServerHandler implements AppServerHandler {

    private final String[] NO_FILES = {};
    protected MavenProject project;

    protected AbstractAppServerHandler(MavenProject project) {
        this.project = project;
    }

    /**
     * Scan the project's output directory for certain files.
     *
     * @param patterns one or more patterns which fit to Maven's include syntax
     * @return list of files found
     */
    protected String[] scanFiles(String... patterns) {
        String buildOutputDir =
                project.getBuild().getOutputDirectory();
        if (buildOutputDir != null) {
            DirectoryScanner directoryScanner = new DirectoryScanner();
            directoryScanner.setBasedir(buildOutputDir);
            directoryScanner.setIncludes(patterns);
            directoryScanner.scan();
            return directoryScanner.getIncludedFiles();
        } else {
            return NO_FILES;
        }

    }

    /**
     * Check whether one of the given file patterns can be found
     * in the project output directory
     *
     * @param patterns patterns to check
     * @return true if the one such file exists least
     */
    protected boolean hasOneOf(String... patterns) {
        return scanFiles(patterns).length > 0;
    }
}
