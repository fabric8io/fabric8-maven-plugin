package io.fabric8.maven.core.handler;

import java.util.List;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.extensions.DaemonSet;
import io.fabric8.kubernetes.api.model.extensions.DaemonSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.DaemonSetSpec;
import io.fabric8.kubernetes.api.model.extensions.DaemonSetSpecBuilder;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.util.kubernetes.KubernetesHelper;
import io.fabric8.maven.docker.config.ImageConfiguration;

/**
 * Created by matthew on 26/10/16.
 */
public class DaemonSetHandler {

    private final PodTemplateHandler podTemplateHandler;

    DaemonSetHandler(PodTemplateHandler podTemplateHandler) {
        this.podTemplateHandler = podTemplateHandler;
    }

    public DaemonSet getDaemonSet(ResourceConfig config,
                                  List<ImageConfiguration> images) {
        return new DaemonSetBuilder()
                .withMetadata(createDaemonSetMetaData(config))
                .withSpec(createDaemonSetSpec(config, images))
                .build();
    }

    // ===========================================================

    private ObjectMeta createDaemonSetMetaData(ResourceConfig config) {
        return new ObjectMetaBuilder()
                .withName(KubernetesHelper.validateKubernetesId(config.getControllerName(), "controller name"))
                .build();
    }

    private DaemonSetSpec createDaemonSetSpec(ResourceConfig config, List<ImageConfiguration> images) {
        return new DaemonSetSpecBuilder()
                .withTemplate(podTemplateHandler.getPodTemplate(config,images))
                .build();
    }

}
