package io.fabric8.maven.core.handler;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.extensions.PetSet;
import io.fabric8.kubernetes.api.model.extensions.PetSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.PetSetSpec;
import io.fabric8.kubernetes.api.model.extensions.PetSetSpecBuilder;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.docker.config.ImageConfiguration;

import java.util.List;

/**
 * Created by matthew on 26/10/16.
 */
public class PetSetHandler {
    private final PodTemplateHandler podTemplateHandler;

    PetSetHandler(PodTemplateHandler podTemplateHandler) {
        this.podTemplateHandler = podTemplateHandler;
    }

    public PetSet getPetSet(ResourceConfig config,
                                    List<ImageConfiguration> images) {
        PetSet petSet = new PetSetBuilder()
                .withMetadata(createPetSetMetaData(config))
                .withSpec(createPetSetSpec(config, images))
                .build();

        return petSet;
    }

    // ===========================================================

    private ObjectMeta createPetSetMetaData(ResourceConfig config) {
        return new ObjectMetaBuilder()
                .withName(KubernetesHelper.validateKubernetesId(config.getReplicaSetName(), "replica set name"))
                .build();
    }

    private PetSetSpec createPetSetSpec(ResourceConfig config, List<ImageConfiguration> images) {
        return new PetSetSpecBuilder()
                .withReplicas(config.getReplicas())
                .withServiceName(config.getReplicaSetName())
                .withTemplate(podTemplateHandler.getPodTemplate(config,images))
                .build();
    }
}
