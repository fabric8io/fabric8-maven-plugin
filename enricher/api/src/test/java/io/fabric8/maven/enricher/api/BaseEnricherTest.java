package io.fabric8.maven.enricher.api;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import java.util.HashMap;
import java.util.List;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JMockit.class)
public class BaseEnricherTest {

    @Mocked
    private EnricherContext enricherContext;

    @Test
    public void should_set_environment_variables_from_resources() {

        // Given
        final DummyBaseEnricher dummyBaseEnricher = new DummyBaseEnricher(enricherContext, "dummy");

        // When
        final KubernetesListBuilder kubernetesListBuilder = createKubernetesList();
        final HashMap<String, String> resourceEnv = new HashMap<>();
        resourceEnv.put("A", "B");

        dummyBaseEnricher.overrideEnvironmentVariables(kubernetesListBuilder, resourceEnv);

        // Then
        kubernetesListBuilder.accept(new TypedVisitor<ContainerBuilder>() {
            @Override
            public void visit(ContainerBuilder element) {
                final List<EnvVar> envVars = element.buildEnv();

                assertThat(envVars)
                    .containsExactly(createEnvVar("A", "B"));
            }
        });
    }

    @Test
    public void should_add_resource_environment_variables_to_container_environment_variables() {

        // Given
        final DummyBaseEnricher dummyBaseEnricher = new DummyBaseEnricher(enricherContext, "dummy");

        // When
        final KubernetesListBuilder kubernetesListBuilder = createKubernetesList(createEnvVar("C", "D"));
        final HashMap<String, String> resourceEnv = new HashMap<>();
        resourceEnv.put("A", "B");


        dummyBaseEnricher.overrideEnvironmentVariables(kubernetesListBuilder, resourceEnv);

        // Then
        kubernetesListBuilder.accept(new TypedVisitor<ContainerBuilder>() {
            @Override
            public void visit(ContainerBuilder element) {
                final List<EnvVar> envVars = element.buildEnv();

                assertThat(envVars)
                    .containsExactlyInAnyOrder(createEnvVar("A", "B"), createEnvVar("C", "D"));
            }
        });

    }

    @Test
    public void should_override_resource_environment_variables_to_container_environment_variables() {

        // Given
        final DummyBaseEnricher dummyBaseEnricher = new DummyBaseEnricher(enricherContext, "dummy");

        // When
        final KubernetesListBuilder kubernetesListBuilder = createKubernetesList(createEnvVar("A", "B"));
        final HashMap<String, String> resourceEnv = new HashMap<>();
        resourceEnv.put("A", "C");

        dummyBaseEnricher.overrideEnvironmentVariables(kubernetesListBuilder, resourceEnv);

        // Then
        kubernetesListBuilder.accept(new TypedVisitor<ContainerBuilder>() {
            @Override
            public void visit(ContainerBuilder element) {
                final List<EnvVar> envVars = element.buildEnv();

                assertThat(envVars)
                    .containsExactlyInAnyOrder(createEnvVar("A", "C"));
            }
        });

    }


    private KubernetesListBuilder createKubernetesList(EnvVar... envVars) {
        final Container container = new ContainerBuilder()
            .withName("test-port-enricher")
            .withImage("test-image")
            .withEnv(envVars)
            .withPorts(new ContainerPortBuilder().withContainerPort(80).withProtocol("TCP").build())
            .build();
        return new KubernetesListBuilder()
            .addNewReplicaSetItem()
            .withNewSpec()
            .withNewTemplate()
            .withNewSpec()
            .withContainers(container)
            .endSpec()
            .endTemplate()
            .endSpec()
            .endReplicaSetItem();
    }

    private EnvVar createEnvVar(String key, String value) {
        return
            new EnvVarBuilder()
                .withName(key)
                .withValue(value)
                .build();
    }

    class DummyBaseEnricher extends BaseEnricher {
        public DummyBaseEnricher(EnricherContext enricherContext, String name) {
            super(enricherContext, name);
        }
    }
}
