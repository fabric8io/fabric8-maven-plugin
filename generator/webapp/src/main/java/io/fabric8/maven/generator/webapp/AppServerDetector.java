package io.fabric8.maven.generator.webapp;

import io.fabric8.maven.generator.api.MavenGeneratorContext;
import io.fabric8.maven.generator.webapp.handler.JettyAppSeverHandler;
import io.fabric8.maven.generator.webapp.handler.TomcatAppSeverHandler;
import io.fabric8.maven.generator.webapp.handler.WildFlyAppSeverHandler;
import org.apache.maven.project.MavenProject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * @author kameshs
 */
class AppServerDetector {

    private final List<? extends AppServerHandler> serverHandlers;
    private final AppServerHandler defaultHandler;
    private final HashMap<String, AppServerHandler> serverHandlerMap;

    AppServerDetector(MavenProject project) {
        // Add new handlers to this list for new appservers
        serverHandlers =
            Arrays.asList(
                new JettyAppSeverHandler(project),
                new WildFlyAppSeverHandler(project),
                defaultHandler = new TomcatAppSeverHandler(project)
                         );
        serverHandlerMap = new HashMap<>();
        for (AppServerHandler handler : serverHandlers) {
            serverHandlerMap.put(handler.getName(), handler);
        }
    }

    AppServerHandler detect(String server) {
        if (server != null && serverHandlerMap.containsKey(server)) {
            return serverHandlerMap.get(server);
        }
        for (AppServerHandler handler : serverHandlers) {
            if (handler.isApplicable()) {
                return handler;
            }
        }
        return defaultHandler;
    }
}
