package io.fabric8.maven.generator.webapp.handler;

import java.io.File;

import io.fabric8.maven.generator.api.DefaultImageLookup;
import io.fabric8.maven.generator.webapp.AppServerHandler;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.DirectoryScanner;

/**
 * @author kameshs
 */
public abstract class AbstractAppServerHandler implements AppServerHandler {

    protected final DefaultImageLookup imageLookup;
    protected final MavenProject project;
    private final String name;

    protected AbstractAppServerHandler(String name, MavenProject project) {
        this.project = project;
        this.name = name;
        this.imageLookup = new DefaultImageLookup(this.getClass());
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Scan the project's output directory for certain files.
     *
     * @param patterns one or more patterns which fit to Maven's include syntax
     * @return list of files found
     */
    protected String[] scanFiles(String... patterns) {
        String buildOutputDir = project.getBuild().getOutputDirectory();
        if (buildOutputDir != null && new File(buildOutputDir).exists()) {
            DirectoryScanner directoryScanner = new DirectoryScanner();
            directoryScanner.setBasedir(buildOutputDir);
            directoryScanner.setIncludes(patterns);
            directoryScanner.scan();
            return directoryScanner.getIncludedFiles();
        } else {
            return new String[0];
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
