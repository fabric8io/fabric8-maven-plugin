package io.fabric8.maven.enricher.standard;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class ConfigMapEnricher extends BaseEnricher {

    protected static final String PREFIX_ANNOTATION = "maven.fabric8.io/cm/";

    public ConfigMapEnricher(MavenEnricherContext enricherContext) {
        super(enricherContext, "fmp-configmap-file");
    }

    @Override
    public void addMissingResources(KubernetesListBuilder builder) {
        addAnnotations(builder);
    }

    private void addAnnotations(KubernetesListBuilder builder) {
        builder.accept(new TypedVisitor<ConfigMapBuilder>() {

            @Override
            public void visit(ConfigMapBuilder element) {
                final Map<String, String> annotations = element.buildMetadata().getAnnotations();
                try {
                    final Map<String, String> configMapAnnotations = createConfigMapFromAnnotations(annotations);
                    element.addToData(configMapAnnotations);
                } catch (IOException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        });
    }

    private Map<String, String> createConfigMapFromAnnotations(final Map<String, String> annotations) throws IOException {
        final Set<Map.Entry<String, String>> entries = annotations.entrySet();
        final Map<String, String> configMapFileLocations = new HashMap<>();

        for(Iterator<Map.Entry<String, String>> it = entries.iterator(); it.hasNext(); ) {
            Map.Entry<String, String> entry = it.next();
            final String key = entry.getKey();

            if(key.startsWith(PREFIX_ANNOTATION)) {
                configMapFileLocations.put(getOutput(key), readContent(entry.getValue()));
                it.remove();
            }
        }

        return configMapFileLocations;
    }

    private String readContent(String location) throws IOException {
        return new String(Files.readAllBytes(Paths.get(location)));
    }

    private String getOutput(String key) {
        return key.substring(PREFIX_ANNOTATION.length());
    }

}
