package io.fabric8.maven.generator.webapp;

import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.DirectoryScanner;

/**
 * @author kameshs
 */
public abstract class AbstractAppServerDetector implements  AppServerDetector {

    protected MavenProject project;

    protected AbstractAppServerDetector(MavenProject project){
        this.project = project;
    }

    /**
     *
     * @param pattern
     * @return
     */
    protected String[] scanFiles(String... pattern) {
        String buildOutputDir =
                project.getBuild().getOutputDirectory();
        DirectoryScanner directoryScanner = new DirectoryScanner();
        directoryScanner.setBasedir(buildOutputDir);
        directoryScanner.setIncludes(pattern);
        directoryScanner.scan();
        return directoryScanner.getIncludedFiles();
    }

    protected abstract boolean hasPlugins();
}
