package io.fabric8.maven.enricher.standard.openshift;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceSpec;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import io.fabric8.openshift.api.model.*;

import static io.fabric8.maven.core.util.kubernetes.KubernetesResourceUtil.removeItemFromKubernetesBuilder;

public class ProjectEnricher extends BaseEnricher {
    static final String ENRICHER_NAME = "fmp-openshift-project";

    public ProjectEnricher(MavenEnricherContext context) {
        super(context, ENRICHER_NAME);
    }

    @Override
    public void create(PlatformMode platformMode, KubernetesListBuilder builder) {
        if(platformMode == PlatformMode.openshift) {
            for(HasMetadata item : builder.buildItems()) {
                if(item instanceof Namespace) {
                    Project project = convert(item);
                    removeItemFromKubernetesBuilder(builder, item);
                    builder.addToProjectItems(project);
                }
            }
        }
    }

    private Project convert(HasMetadata item) {
        Namespace namespace = (Namespace) item;
        namespace.getMetadata();

        ProjectBuilder builder = new ProjectBuilder();
        builder.withMetadata(namespace.getMetadata());


        if (namespace.getSpec() != null) {
            NamespaceSpec namespaceSpec = namespace.getSpec();
            ProjectSpec projectSpec = new ProjectSpec();
            if (namespaceSpec.getFinalizers() != null) {
                projectSpec.setFinalizers(namespaceSpec.getFinalizers());
            }
            namespaceSpec.getAdditionalProperties()
                    .forEach((k, v) -> projectSpec.setAdditionalProperty(k, v));

            builder.withSpec(projectSpec);
        }

        if (namespace.getStatus() != null) {
            ProjectStatus status = new ProjectStatusBuilder()
                    .withPhase(namespace.getStatus().getPhase()).build();

            namespace.getStatus().getAdditionalProperties()
                    .forEach((k, v) -> status.setAdditionalProperty(k, v));
        }

        Project project = builder.build();

        namespace.getAdditionalProperties()
                    .forEach((k,v)-> project.setAdditionalProperty(k,v));

        return project;
    }
}
