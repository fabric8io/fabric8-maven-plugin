package io.fabric8.maven.generator.api;

import java.util.Map;

import org.apache.maven.project.MavenProject;

public interface PortsExtractor {

    Map<String, Integer> extract(MavenProject project);

}
