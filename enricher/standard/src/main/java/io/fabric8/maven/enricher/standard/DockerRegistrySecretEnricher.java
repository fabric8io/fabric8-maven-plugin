package io.fabric8.maven.enricher.standard;

import io.fabric8.maven.core.util.DockerServerUtil;
import io.fabric8.maven.core.util.SecretConstants;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.utils.Strings;

import java.util.HashMap;
import java.util.Map;

public class DockerRegistrySecretEnricher extends SecretEnricher {
    final private static String ANNOTATION_KEY = "maven.fabric8.io/dockerServerId";
    final private static String ENRICHER_NAME = "fmp-docker-registry-secret";


    public DockerRegistrySecretEnricher(EnricherContext buildContext) {
        super(buildContext, ENRICHER_NAME);
    }

    @Override
    protected String getAnnotationKey() {
        return ANNOTATION_KEY;
    }

    @Override
    protected Map<String, String> generateData(String dockerId) {
        String dockerSecret = DockerServerUtil.getDockerJsonConfigString(getContext().getSettings(), dockerId);
        if (Strings.isNullOrBlank(dockerSecret)) {
            return null;
        }

        Map<String, String> data = new HashMap<>();
        data.put(SecretConstants.DOCKER_DATA_KEY, encode(dockerSecret));
        return data;
    }
}
