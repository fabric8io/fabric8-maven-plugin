package io.fabric8.maven.core.handler;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.extensions.*;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.docker.config.ImageConfiguration;

import java.util.List;

/**
 * Handler for StatefulSets
 *
 * @author matthew on 26/10/16.
 */
public class StatefulSetHandler {
    private final PodTemplateHandler podTemplateHandler;

    StatefulSetHandler(PodTemplateHandler podTemplateHandler) {
        this.podTemplateHandler = podTemplateHandler;
    }

    public StatefulSet getStatefulSet(ResourceConfig config,
                                      List<ImageConfiguration> images) {

        return new StatefulSetBuilder()
                .withMetadata(createStatefulSetMetaData(config))
                .withSpec(createStatefulSetSpec(config, images))
                .build();
    }

    // ===========================================================

    private ObjectMeta createStatefulSetMetaData(ResourceConfig config) {
        return new ObjectMetaBuilder()
                .withName(KubernetesHelper.validateKubernetesId(config.getControllerName(), "controller name"))
                .build();
    }

    private StatefulSetSpec createStatefulSetSpec(ResourceConfig config, List<ImageConfiguration> images) {
        return new StatefulSetSpecBuilder()
                .withReplicas(config.getReplicas())
                .withServiceName(config.getControllerName())
                .withTemplate(podTemplateHandler.getPodTemplate(config,images))
                .build();
    }
}
