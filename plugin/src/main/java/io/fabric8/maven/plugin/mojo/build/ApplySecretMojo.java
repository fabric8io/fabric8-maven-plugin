package io.fabric8.maven.plugin.mojo.build;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;

@Mojo(name = "apply-secrets", requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.INSTALL)
public class ApplySecretMojo extends ApplyMojo {
    public static final String DEFAULT_KUBERNETES_MANIFEST = "${project.build.outputDirectory}/META-INF/fabric8/secrets/kubernetes.yml";
    public static final String DEFAULT_OPENSHIFT_MANIFEST = "${project.build.outputDirectory}/META-INF/fabric8/secrets/openshift.yml";

    /**
     * The generated kubernetes YAML file
     */
    @Parameter(property = "fabric8.kubernetesSecretsManifest", defaultValue = DEFAULT_KUBERNETES_MANIFEST)
    private File kubernetesSecretsManifest;

    /**
     * The generated openshift YAML file
     */
    @Parameter(property = "fabric8.openshiftSecretsManifest", defaultValue = DEFAULT_OPENSHIFT_MANIFEST)
    private File openshiftSecretsManifest;

    @Override
    public File getKubernetesManifest() {
        return this.kubernetesSecretsManifest;
    }

    @Override
    protected File getOpenShiftManifest() {
        return this.openshiftSecretsManifest;
    }
}
