package io.fabric8.maven.enricher.fabric8;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;
import io.fabric8.maven.enricher.api.util.InitContainerHandler;

import java.util.*;

/**
 * Enriches declarations with auto-TLS annotations, required secrets reference,
 * mounted volumes and PEM to keystore converter init container.
 *
 * This is opt-in so should not be added to default enrichers as it only works for
 * OpenShift.
 */
public class AutoTLSEnricher extends BaseEnricher {
    static final String ENRICHER_NAME = "fmp-autotls";
    static final String AUTOTLS_ANNOTATION_KEY = "service.alpha.openshift.io/serving-cert-secret-name";

    private String secretName;

    private final InitContainerHandler initContainerHandler;

    enum Config implements Configs.Key {
        tlsSecretName,

        tlsSecretVolumeMountPoint  {{ d = "/var/run/secrets/fabric8.io/tls-pem"; }},

        tlsSecretVolumeName        {{ d = "tls-pem"; }},

        jksVolumeMountPoint        {{ d = "/var/run/secrets/fabric8.io/tls-jks"; }},

        jksVolumeName              {{ d = "tls-jks"; }},

        pemToJKSInitContainerImage {{ d = "jimmidyson/pemtokeystore:v0.1.0"; }},

        pemToJKSInitContainerName  {{ d = "tls-jks-converter"; }},

        keystoreFileName           {{ d = "keystore.jks"; }},

        keystorePassword           {{ d = "changeit"; }},

        keystoreCertAlias          {{ d = "server"; }};

        public String def() { return d; } protected String d;
    }

    public AutoTLSEnricher(EnricherContext buildContext) {
        super(buildContext, ENRICHER_NAME);

        this.secretName = getConfig(Config.tlsSecretName, getProject().getArtifactId() + "-tls");
        this.initContainerHandler = new InitContainerHandler(buildContext.getLog());
    }

    @Override
    public Map<String, String> getAnnotations(Kind kind) {
        if (!isOpenShiftMode() || kind != Kind.SERVICE) {
            return null;
        }
        return Collections.singletonMap(AUTOTLS_ANNOTATION_KEY, this.secretName);
    }

    @Override
    public void addMissingResources(KubernetesListBuilder builder) {
        if (!isOpenShiftMode()) {
            return;
        }

        builder.accept(new TypedVisitor<PodSpecBuilder>() {
            @Override
            public void visit(PodSpecBuilder builder) {
                String tlsSecretVolumeName = getConfig(Config.tlsSecretVolumeName);
                if (!isVolumeAlreadyExists(builder.buildVolumes(), tlsSecretVolumeName)) {
                    builder.addNewVolume().withName(tlsSecretVolumeName).withNewSecret()
                            .withSecretName(AutoTLSEnricher.this.secretName).endSecret().endVolume();
                }
                String jksSecretVolumeName = getConfig(Config.jksVolumeName);
                if (!isVolumeAlreadyExists(builder.buildVolumes(), jksSecretVolumeName)) {
                    builder.addNewVolume().withName(jksSecretVolumeName).withNewEmptyDir().withMedium("Memory").endEmptyDir().endVolume();
                }
            }

            private boolean isVolumeAlreadyExists(List<Volume> volumes, String volumeName) {
                for (Volume v : volumes) {
                    if (volumeName.equals(v.getName())) {
                        return true;
                    }
                }
                return false;
            }
        });

        builder.accept(new TypedVisitor<ContainerBuilder>() {
            @Override
            public void visit(ContainerBuilder builder) {
                String tlsSecretVolumeName = getConfig(Config.tlsSecretVolumeName);
                if (!isVolumeMountAlreadyExists(builder.buildVolumeMounts(), tlsSecretVolumeName)) {
                    builder.addNewVolumeMount().withName(tlsSecretVolumeName)
                            .withMountPath(getConfig(Config.tlsSecretVolumeMountPoint)).withReadOnly(true)
                            .endVolumeMount();
                }

                String jksVolumeName = getConfig(Config.jksVolumeName);
                if (!isVolumeMountAlreadyExists(builder.buildVolumeMounts(), jksVolumeName)) {
                    builder.addNewVolumeMount().withName(jksVolumeName)
                            .withMountPath(getConfig(Config.jksVolumeMountPoint)).withReadOnly(true).endVolumeMount();
                }
            }

            private boolean isVolumeMountAlreadyExists(List<VolumeMount> volumes, String volumeName) {
                for (VolumeMount v : volumes) {
                    if (volumeName.equals(v.getName())) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    @Override
    public void adapt(KubernetesListBuilder builder) {
        if (!isOpenShiftMode()) {
            return;
        }

        builder.accept(new TypedVisitor<PodTemplateSpecBuilder>() {
            @Override
            public void visit(PodTemplateSpecBuilder builder) {
                initContainerHandler.appendInitContainer(builder, createInitContainer());
            }

            private Container createInitContainer() {
                return new ContainerBuilder()
                        .withName(getConfig(Config.pemToJKSInitContainerName))
                        .withImage(getConfig(Config.pemToJKSInitContainerImage))
                        .withImagePullPolicy("IfNotPresent")
                        .withArgs(createArgsArray())
                        .withVolumeMounts(createMounts())
                        .build();
            }

            private List<String> createArgsArray() {
                List<String> ret = new ArrayList<>();
                ret.add("-cert-file");
                ret.add(getConfig(Config.keystoreCertAlias) + "=/tls-pem/tls.crt");
                ret.add("-key-file");
                ret.add(getConfig(Config.keystoreCertAlias) + "=/tls-pem/tls.key");
                ret.add("-keystore");
                ret.add("/tls-jks/" + getConfig(Config.keystoreFileName));
                ret.add("-keystore-password");
                ret.add(getConfig(Config.keystorePassword));
                return ret;
            }

            private List<VolumeMount> createMounts() {

                VolumeMount pemMountPoint = new VolumeMountBuilder()
                        .withName(getConfig(Config.tlsSecretVolumeName))
                        .withMountPath("/tls-pem")
                        .build();
                VolumeMount jksMountPoint = new VolumeMountBuilder()
                        .withName(getConfig(Config.jksVolumeName))
                        .withMountPath("/tls-jks")
                        .build();

                return Arrays.asList(pemMountPoint, jksMountPoint);
            }
        });
    }

}
