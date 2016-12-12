package io.fabric8.maven.generator.api;

import org.apache.maven.project.MavenProject;

import java.util.Map;

public interface PortsExtractor {

    Map<String, Integer> extract(MavenProject project);

}
