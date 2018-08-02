package io.fabric8.maven.enricher.fabric8;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import io.fabric8.kubernetes.api.model.HTTPHeader;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.ProbeFluent;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.enricher.api.AbstractHealthCheckEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.Xpp3Dom;


/**
 * Configures the health checks for a Vert.x project. Unlike other enricher this enricher extract the configuration from
 * the following project properties: `vertx.health.port`, `vertx.health.path`.
 * <p>
 * It builds a liveness probe and a readiness probe using:
 * <p>
 * <ul>
 * <li>`vertx.health.port` - the port, 8080 by default, a negative number disables the health check</li>
 * <li>`vertx.health.path` - the path, / by default, an empty (non null) value disables the health check</li>
 * <li>`vertx.health.scheme` - the scheme, HTTP by default, can be set to HTTPS (adjusts the port accordingly)</li>
 * </ul>
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class VertxHealthCheckEnricher extends AbstractHealthCheckEnricher {

    private static final String VERTX_MAVEN_PLUGIN_GA = "io.fabric8:vertx-maven-plugin";
    private static final String VERTX_GROUPID = "io.vertx";

    private static final int DEFAULT_MANAGEMENT_PORT = 8080;
    private static final String SCHEME_HTTP = "HTTP";

    private static final String VERTX_HEALTH = "vertx.health.";
    private static final Function<? super String, String> TRIM = new Function<String, String>() {
        @Nullable
        @Override
        public String apply(@Nullable String input) {
            return input == null ? null : input.trim();
        }
    };

    public VertxHealthCheckEnricher(EnricherContext buildContext) {
        super(buildContext, "vertx-health-check");
    }

    @Override
    protected Probe getReadinessProbe() {
        return discoverVertxHealthCheck(true);
    }

    @Override
    protected Probe getLivenessProbe() {
        return discoverVertxHealthCheck(false);
    }


    private boolean isApplicable() {
        return MavenUtil.hasPlugin(getProject(), VERTX_MAVEN_PLUGIN_GA)
                || MavenUtil.hasDependencyOnAnyArtifactOfGroup(getProject(), VERTX_GROUPID);
    }

    private String getSpecificPropertyName(boolean readiness, String attribute) {
        if (readiness) {
            return VERTX_HEALTH + "readiness." + attribute;
        } else {
            return VERTX_HEALTH + "liveness." + attribute;
        }
    }

    private Probe discoverVertxHealthCheck(boolean readiness) {
        if (!isApplicable()) {
            return null;
        }
        // We don't allow to set the HOST, because it should rather be configured in the HTTP header (Host header)
        // cf. https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-probes/

        String type = getStringValue("type", readiness).or("http").toUpperCase();
        Optional<Integer> port = getIntegerValue("port", readiness);
        Optional<String> portName = getStringValue("port-name", readiness);
        String path = getStringValue("path", readiness)
                .transform(new Function<String, String>() {
                    @Override
                    public String apply(String input) {
                        if (input.isEmpty() || input.startsWith("/")) {
                            return input;
                        }
                        return "/" + input;
                    }
                })
                .orNull();
        String scheme = getStringValue("scheme", readiness).or(SCHEME_HTTP).toUpperCase();
        Optional<Integer> initialDelay = getIntegerValue("initial-delay", readiness);
        Optional<Integer> period = getIntegerValue("period", readiness);
        Optional<Integer> timeout = getIntegerValue("timeout", readiness);
        Optional<Integer> successThreshold = getIntegerValue("success-threshold", readiness);
        Optional<Integer> failureThreshold = getIntegerValue("failure-threshold", readiness);
        List<String> command = getListValue("command", readiness).or(Collections.<String>emptyList());
        Map<String, String> headers = getMapValue("headers", readiness).or(Collections.<String, String>emptyMap());


        // Validate
        // Port and port-name cannot be set at the same time
        if (port.isPresent() && portName.isPresent()) {
            log.error("Invalid health check configuration - both 'port' and 'port-name' are set, only one of them can be used");
            throw new IllegalArgumentException("Invalid health check configuration - both 'port' and 'port-name' are set, only one of them can be used");
        }

        if (type.equalsIgnoreCase("TCP")) {
            if (!port.isPresent() && !portName.isPresent()) {
                log.info("TCP health check disabled (port not set)");
                return null;
            }
            if (port.isPresent() && port.get() <= 0) {
                log.info("TCP health check disabled (port set to a negative number)");
                return null;
            }
        } else if (type.equalsIgnoreCase("EXEC")) {
            if (command.isEmpty()) {
                log.info("TCP health check disabled (command not set)");
                return null;
            }
        } else if (type.equalsIgnoreCase("HTTP")) {
            if (port.isPresent() && port.get() <= 0) {
                log.info("HTTP health check disabled (port set to " + port.get());
                return null;
            }

            if (path == null) {
                log.info("HTTP health check disabled (path not set)");
                return null;
            }

            if (path.isEmpty()) {
                log.info("HTTP health check disabled (the path is empty)");
                return null;
            }

            // Set default port if not set
            if (!port.isPresent() && !portName.isPresent()) {
                log.info("Using default management port (8080) for HTTP health check probe");
                port = Optional.of(DEFAULT_MANAGEMENT_PORT);
            }

        } else {
            log.error("Invalid health check configuration - Unknown probe type, only 'exec', 'tcp' and 'http' (default) are supported");
            throw new IllegalArgumentException("Invalid health check configuration - Unknown probe type, only 'exec', 'tcp' and 'http' (default) are supported");
        }

        // Time to build the probe
        ProbeBuilder builder = new ProbeBuilder();
        if (initialDelay.isPresent()) {
            builder.withInitialDelaySeconds(initialDelay.get());
        }
        if (period.isPresent()) {
            builder.withPeriodSeconds(period.get());
        }
        if (timeout.isPresent()) {
            builder.withTimeoutSeconds(timeout.get());
        }
        if (successThreshold.isPresent()) {
            builder.withSuccessThreshold(successThreshold.get());
        }
        if (failureThreshold.isPresent()) {
            builder.withFailureThreshold(failureThreshold.get());
        }

        switch (type) {
            case "HTTP":
                ProbeFluent.HttpGetNested<ProbeBuilder> http = builder.withNewHttpGet()
                        .withScheme(scheme)
                        .withPath(path);
                if (port.isPresent()) {
                    http.withNewPort(port.get());
                }
                if (portName.isPresent()) {
                    http.withNewPort(portName.get());
                }
                if (!headers.isEmpty()) {
                    List<HTTPHeader> list = new ArrayList<>();
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        list.add(new HTTPHeader(entry.getKey(), entry.getValue()));
                    }
                    http.withHttpHeaders(list);
                }
                http.endHttpGet();
                break;
            case "TCP":
                ProbeFluent.TcpSocketNested<ProbeBuilder> tcp = builder.withNewTcpSocket();
                if (port.isPresent()) {
                    tcp.withNewPort(port.get());
                }
                if (portName.isPresent()) {
                    tcp.withNewPort(portName.get());
                }
                tcp.endTcpSocket();
                break;
            case "EXEC":
                builder.withNewExec().withCommand(command).endExec();
        }

        return builder.build();
    }

    private Optional<String> getStringValue(String attribute, boolean readiness) {

        String specific = getSpecificPropertyName(readiness, attribute);
        String generic = VERTX_HEALTH + attribute;
        // Check if we have the specific user property.
        String property = getContext().getProject().getProperties().getProperty(specific);
        if (property != null) {
            return Optional.of(property).transform(TRIM);
        }

        property = getContext().getProject().getProperties().getProperty(generic);
        if (property != null) {
            return Optional.of(property).transform(TRIM);
        }


        String[] specificPath = new String[]{
                readiness ? "readiness" : "liveness",
                attribute
        };

        Optional<String> config = getValueFromConfig(specificPath).transform(TRIM);
        if (!config.isPresent()) {
            // Generic path.
            return getValueFromConfig(attribute).transform(TRIM);
        } else {
            return config;
        }

    }

    private Optional<List<String>> getListValue(String attribute, boolean readiness) {
        String[] path = new String[]{
                readiness ? "readiness" : "liveness",
                attribute
        };

        Optional<Xpp3Dom> element = getElement(path);
        if (!element.isPresent()) {
            element = getElement(attribute);
        }

        return element.transform(new Function<Xpp3Dom, List<String>>() {
            @Override
            public List<String> apply(Xpp3Dom input) {
                Xpp3Dom[] children = input.getChildren();
                List<String> list = new ArrayList<>();
                for (Xpp3Dom child : children) {
                    list.add(child.getValue());
                }
                return list;
            }
        });
    }

    private Optional<Map<String, String>> getMapValue(String attribute, boolean readiness) {
        String[] path = new String[]{
                readiness ? "readiness" : "liveness",
                attribute
        };

        Optional<Xpp3Dom> element = getElement(path);
        if (!element.isPresent()) {
            element = getElement(attribute);
        }

        return element.transform(new Function<Xpp3Dom, Map<String, String>>() {
            @Override
            public Map<String, String> apply(Xpp3Dom input) {
                Xpp3Dom[] children = input.getChildren();
                Map<String, String> map = new LinkedHashMap<>();
                for (Xpp3Dom child : children) {
                    map.put(child.getName(), child.getValue());
                }
                return map;
            }
        });
    }


    private Optional<Integer> getIntegerValue(String attribute, boolean readiness) {
        return getStringValue(attribute, readiness)
                .transform(new Function<String, Integer>() {
                    @Override
                    public Integer apply(String input) {
                        return Integer.valueOf(input);
                    }
                });
    }

    private Optional<String> getValueFromConfig(String... keys) {
        return getElement(keys).transform(new Function<Xpp3Dom, String>() {
            @Override
            @Nullable
            public String apply(Xpp3Dom input) {
                return input.getValue();
            }
        });
    }

    private Optional<Xpp3Dom> getElement(String... path) {
        Plugin plugin = getContext().getProject().getPlugin("io.fabric8:fabric8-maven-plugin");
        if (plugin == null) {
            getLog().warn("Unable to find the fabric8-maven-plugin in the project, weird...");
            return Optional.absent();
        }

        Xpp3Dom configuration = (Xpp3Dom) plugin.getConfiguration();
        if (configuration == null) {
            return Optional.absent();
        }

        String[] roots = new String[]{"enricher", "config", "vertx-health-check"};
        List<String> absolute = new ArrayList<>();
        absolute.addAll(Arrays.asList(roots));
        absolute.addAll(Arrays.asList(path));
        Xpp3Dom root = configuration;
        for (String key : absolute) {
            root = root.getChild(key);
            if (root == null) {
                return Optional.absent();
            }
        }
        return Optional.of(root);
    }

}
