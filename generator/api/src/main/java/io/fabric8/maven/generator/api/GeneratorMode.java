package io.fabric8.maven.generator.api;

/**
 * Modes which influence how generators are creating image configurations
 *
 * @author roland
 * @since 03.10.18
 */
public enum GeneratorMode {

    /**
     * Regular build mode. Image will be created which are used in production
     */
    BUILD,

    /**
     * Special generation mode used for watching
     */
    WATCH,

    /**
     * Generate image suitable for remote debugging
     */
    DEBUG
}
