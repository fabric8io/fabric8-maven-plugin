package io.fabric8.maven.generator.webapp;

import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.DirectoryScanner;

import java.util.List;

/**
 * @author kameshs
 */
public interface AppServerDetector {

    /**
     *
     * @return
     */
    boolean isApplicable();

    /**
     *
     * @return
     */
    String getDeploymentDir();

    /**
     *
     * @return
     */
    String getCommand();

    /**
     *
     * @return
     */
    String getFrom();

    /**
     *
     * @return
     */
    AppServerDetectorFactory.Kind getKind();


    /**
     * 
     * @return
     */
    List<String> exposedPorts();


}
