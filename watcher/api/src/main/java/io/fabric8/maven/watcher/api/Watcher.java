package io.fabric8.maven.watcher.api;

import java.util.List;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.maven.core.config.Named;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.docker.config.ImageConfiguration;


public interface Watcher extends Named {

    /**
     * Check whether this watcher should kick in.
     *
     * @return true if the watcher is applicable
     * @param configs all image configurations
     */
    boolean isApplicable(List<ImageConfiguration> configs, Set<HasMetadata> resources, PlatformMode mode);


    /**
     * Watch the resources and kick a rebuild when they change.
     *
     * @param configs all image configurations
     */
    void watch(List<ImageConfiguration> configs, Set<HasMetadata> resources, PlatformMode mode) throws Exception;

}
