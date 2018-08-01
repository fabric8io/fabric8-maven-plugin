package io.fabric8.maven.core.handler;

import java.util.List;

import io.fabric8.kubernetes.api.model.Job;
import io.fabric8.kubernetes.api.model.JobBuilder;
import io.fabric8.kubernetes.api.model.JobSpec;
import io.fabric8.kubernetes.api.model.JobSpecBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.util.kubernetes.KubernetesHelper;
import io.fabric8.maven.docker.config.ImageConfiguration;

/**
 * Handler for Jobs
 *
 * @author matthew on 11/02/17.
 */
public class JobHandler {
    private final PodTemplateHandler podTemplateHandler;

    JobHandler(PodTemplateHandler podTemplateHandler) {
        this.podTemplateHandler = podTemplateHandler;
    }

    public Job getJob(ResourceConfig config,
                                      List<ImageConfiguration> images) {
        return new JobBuilder()
                .withMetadata(createJobSpecMetaData(config))
                .withSpec(createJobSpec(config, images))
                .build();
    }

    // ===========================================================

    private ObjectMeta createJobSpecMetaData(ResourceConfig config) {
        return new ObjectMetaBuilder()
                .withName(KubernetesHelper.validateKubernetesId(config.getControllerName(), "controller name"))
                .build();
    }

    private JobSpec createJobSpec(ResourceConfig config, List<ImageConfiguration> images) {
        return new JobSpecBuilder()
                .withTemplate(podTemplateHandler.getPodTemplate(config, images))
                .build();
    }
}
