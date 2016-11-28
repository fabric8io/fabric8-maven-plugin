package io.fabric8.maven.generator.vertx;

import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import io.fabric8.maven.core.util.PrefixedLogger;
import io.fabric8.maven.generator.api.support.AbstractPortsExtractor;

import static io.fabric8.maven.generator.vertx.Constants.*;


public class VertxPortsExtractor extends AbstractPortsExtractor {


    public VertxPortsExtractor(PrefixedLogger log) {
        super(log);
    }

    @Override
    public String getConfigPathPropertyName() {
        return VERTX_CONFIG_PROPERTY;
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
        Xpp3Dom config = configuration.getChild(CONFIG);
        return config != null ? config.getValue() : null;
    }
}
