package io.fabric8.maven.plugin.watcher;

import io.fabric8.maven.core.util.PrefixedLogger;

/**
 * The base class of watchers.
 */
public abstract class BaseWatcher implements Watcher {

    private WatcherContext context;

    private String name;

    protected final PrefixedLogger log;

    public BaseWatcher(WatcherContext context, String name) {
        this.context = context;
        this.name = name;
        this.log = new PrefixedLogger(name, context.getLogger());
    }

    public WatcherContext getContext() {
        return context;
    }

    @Override
    public String getName() {
        return name;
    }

}
