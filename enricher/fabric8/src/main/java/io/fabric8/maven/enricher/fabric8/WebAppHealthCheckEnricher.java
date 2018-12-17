package io.fabric8.maven.enricher.fabric8;

import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import org.apache.commons.lang3.StringUtils;

public class WebAppHealthCheckEnricher extends AbstractHealthCheckEnricher {


    private static final int DEFAULT_DELAY_READNESS = 10;
    private static final int DEFAULT_DELAY_LIVENESS = 180;


    public WebAppHealthCheckEnricher(MavenEnricherContext buildContext) {
        super(buildContext, "f8-healthcheck-webapp");
    }

    // Available configuration keys
    private enum Config implements Configs.Key {

        scheme {{
            d = "HTTP";
        }},
        port {{
            d = "8080";
        }},
        path {{
            d = "";
        }},
        initialDelay {{
            d = "-1";
        }};

        protected String d;

        public String def() {
            return d;
        }
    }

    @Override
    protected Probe getLivenessProbe() {
        return getProbe(false);
    }

    @Override
    protected Probe getReadinessProbe() {
        return getProbe(true);
    }

    private boolean isApplicable() {
        return getContext()
            .hasPlugin("org.apache.maven.plugins", "maven-war-plugin") &&
            StringUtils.isNotEmpty(Configs.asString(getConfig(Config.path)));
    }

    private Probe getProbe(boolean readiness) {
        if (!isApplicable()) {
            return null;
        }

        Integer port = getPort();
        String scheme = getScheme().toUpperCase();
        String path = getPath();

        return new ProbeBuilder().
            withNewHttpGet().withNewPort(port).withPath(path).withScheme(scheme).endHttpGet().
            withInitialDelaySeconds(getInitialDelay(readiness)).build();

    }

    private int getInitialDelay(boolean readness) {
        final int initialDelay = Configs.asInt(getConfig(Config.initialDelay));

        if (initialDelay < 0) {
            return readness ? DEFAULT_DELAY_READNESS : DEFAULT_DELAY_LIVENESS;
        }

        return  initialDelay;
    }

    protected String getScheme() {
        return Configs.asString(getConfig(Config.scheme));
    }

    protected int getPort() {
        return Configs.asInt(getConfig(Config.port));
    }

    protected String getPath() {
        return Configs.asString(getConfig(Config.path));
    }
}
