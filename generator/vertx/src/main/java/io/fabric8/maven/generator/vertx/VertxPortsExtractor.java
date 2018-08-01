package io.fabric8.maven.generator.vertx;

import io.fabric8.maven.core.util.PrefixedLogger;
import io.fabric8.maven.generator.api.support.AbstractPortsExtractor;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;


public class VertxPortsExtractor extends AbstractPortsExtractor {


    public VertxPortsExtractor(PrefixedLogger log) {
        super(log);
    }

    @Override
    public String getConfigPathPropertyName() {
        return "vertx.config";
    }

    @Override
    public String getConfigPathFromProject(MavenProject project) {
        Plugin plugin = project.getPlugin(Constants.VERTX_MAVEN_PLUGIN_GA);
        if (plugin == null) {
            return null;
        }

        Xpp3Dom configuration = (Xpp3Dom) plugin.getConfiguration();
        if (configuration == null) {
            return null;
        }
        Xpp3Dom config = configuration.getChild("config");
        return config != null ? config.getValue() : null;
    }
}
