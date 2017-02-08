package io.fabric8.maven.plugin.watcher;

import java.util.List;

import io.fabric8.maven.core.config.Named;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.docker.config.ImageConfiguration;

/**
 *
 */
public interface Watcher extends Named {

    /**
     * Check whether this watcher should kick in.
     *
     * @return true if the watcher is applicable
     * @param configs all image configurations
     */
    boolean isApplicable(List<ImageConfiguration> configs, PlatformMode mode);


    /**
     * Watch the resources and kick a rebuild when they change.
     *
     * @param configs all image configurations
     */
    void watch(List<ImageConfiguration> configs, PlatformMode mode);

}
