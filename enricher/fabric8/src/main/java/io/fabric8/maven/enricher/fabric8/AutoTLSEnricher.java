package io.fabric8.maven.enricher.fabric8;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;
import io.fabric8.maven.enricher.api.util.InitContainerHandler;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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

            private JSONObject createInitContainer() {
                JSONObject entry = new JSONObject();
                entry.put("name", getConfig(Config.pemToJKSInitContainerName));
                entry.put("image", getConfig(Config.pemToJKSInitContainerImage));
                entry.put("imagePullPolicy","IfNotPresent");
                entry.put("args", createArgsArray());
                entry.put("volumeMounts", createMounts());
                return entry;
            }

            private JSONArray createArgsArray() {
                JSONArray ret = new JSONArray();
                ret.put("-cert-file");
                ret.put(getConfig(Config.keystoreCertAlias) + "=/tls-pem/tls.crt");
                ret.put("-key-file");
                ret.put(getConfig(Config.keystoreCertAlias) + "=/tls-pem/tls.key");
                ret.put("-keystore");
                ret.put("/tls-jks/" + getConfig(Config.keystoreFileName));
                ret.put("-keystore-password");
                ret.put(getConfig(Config.keystorePassword));
                return ret;
            }

            private JSONArray createMounts() {
                JSONObject pemMountPoint = new JSONObject();
                pemMountPoint.put("name", getConfig(Config.tlsSecretVolumeName));
                pemMountPoint.put("mountPath", "/tls-pem");
                JSONObject jksMountPoint = new JSONObject();
                jksMountPoint.put("name", getConfig(Config.jksVolumeName));
                jksMountPoint.put("mountPath", "/tls-jks");
                JSONArray ret = new JSONArray();
                ret.put(pemMountPoint);
                ret.put(jksMountPoint);
                return ret;
            }
        });
    }

}
