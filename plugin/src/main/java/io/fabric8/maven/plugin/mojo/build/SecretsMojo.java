package io.fabric8.maven.plugin.mojo.build;

import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.config.Profile;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.config.SecretConfig;
import io.fabric8.maven.core.util.*;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.plugin.enricher.EnricherManager;
import io.fabric8.utils.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;

import java.beans.Encoder;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates or copies the Kubernetes JSON secrets file and attaches it to the build so its
 * installed and released to maven repositories like other build artifacts.
 */
@Mojo(name = "secrets", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresDependencyResolution = ResolutionScope.COMPILE)
public class SecretsMojo extends ResourceMojo {

    private final static String[] _enricherBlcakList = null;
    private final static String[] _enricherWhiteList = {SecretConstants.FOLDER_NAME};

    @Override
    protected String[] getEnricherBlackList() {
        return _enricherBlcakList;
    }

    @Override
    protected String[] getEnricherWhiteList() {
        return _enricherWhiteList;
    }

    @Override
    public void executeInternal() throws MojoExecutionException, MojoFailureException {
        this.targetDir = new File(this.targetDir, SecretConstants.FOLDER_NAME);
        super.executeInternal();
    }

    @Override
    protected KubernetesListBuilder generateAppResources(List<ImageConfiguration> images, EnricherManager enricherManager) throws IOException, MojoExecutionException {
        KubernetesListBuilder builder = new KubernetesListBuilder();
        if (resources != null) {
            addConfiguredResources(builder, images);
        }
        return builder;
    }

    @Override
    protected void addConfiguredResources(KubernetesListBuilder builder, List<ImageConfiguration> images) {
        log.verbose("Adding secrets resources from plugin configuration");
        List<SecretConfig> secrets = resources.getSecrets();
        if (secrets == null || secrets.size() == 0) { return; }
        for (int i = 0; i < secrets.size(); i++) {
            SecretConfig secretConfig = secrets.get(i);
            if (Strings.isNullOrBlank(secretConfig.name)) {
                continue;
            }

            Map<String, String> data = new HashMap();
            ObjectMeta metadata = new ObjectMeta();
            String type = "";
            metadata.setNamespace(secretConfig.namespace == null ? "default" : secretConfig.namespace);
            metadata.setName(secretConfig.name);

            // docker-registry
            if (secretConfig.dockerId != null) {
                String dockerSecret = DockerUtil.getDockerJsonConfigString(settings, secretConfig.dockerId);
                if (Strings.isNullOrBlank(dockerSecret)) {
                    continue;
                }
                data.put(SecretConstants.DOCKER_DATA_KEY, Base64Util.encodeToString(dockerSecret));
                type = SecretConstants.DOCKER_CONFIG_TYPE;
            }
            // TODO: generic secret

            if (Strings.isNullOrBlank(type) || data.isEmpty()) {
                continue;
            }

            Secret secret = new Secret(SecretConstants.API_VERSION, data, SecretConstants.KIND, metadata, null, type);
            builder.addToSecretItems(i, secret);
        }
    }
}

