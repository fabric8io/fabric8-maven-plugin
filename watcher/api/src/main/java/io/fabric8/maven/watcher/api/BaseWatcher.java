package io.fabric8.maven.watcher.api;

import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.PrefixedLogger;

/**
 * The base class of watchers.
 */
public abstract class BaseWatcher implements Watcher {

    private WatcherContext context;

    private WatcherConfig config;

    private String name;

    protected final PrefixedLogger log;

    public BaseWatcher(WatcherContext context, String name) {
        this.context = context;
        this.config = new WatcherConfig(context.getProject().getProperties(), name, context.getConfig());
        this.name = name;
        this.log = new PrefixedLogger(name, context.getLogger());
    }

    public WatcherContext getContext() {
        return context;
    }

    protected String getConfig(Configs.Key key) {
        return config.get(key);
    }

    protected String getConfig(Configs.Key key, String defaultVal) {
        return config.get(key, defaultVal);
    }

    @Override
    public String getName() {
        return name;
    }

}
