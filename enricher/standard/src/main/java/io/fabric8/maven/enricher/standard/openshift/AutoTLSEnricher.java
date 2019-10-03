/**
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.maven.enricher.standard.openshift;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
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
    static final String ENRICHER_NAME = "fmp-openshift-autotls";
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

    public AutoTLSEnricher(MavenEnricherContext buildContext) {
        super(buildContext, ENRICHER_NAME);

        this.secretName = getConfig(Config.tlsSecretName, getContext().getGav().getArtifactId() + "-tls");
        this.initContainerHandler = new InitContainerHandler(buildContext.getLog());
    }

    @Override
    public void create(PlatformMode platformMode, KubernetesListBuilder builder) {
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

        builder.accept(new TypedVisitor<ServiceBuilder>() {
            @Override
            public void visit(ServiceBuilder service) {
                /*
                 * Set the service.alpha.openshift.io/serving-cert-secret-name annotation on your
                 * service with the value set to the name you want to use for your secret.
                 *
                 * https://docs.openshift.com/online/dev_guide/secrets.html#service-serving-certificate-secrets
                 */
                service.editOrNewMetadata()
                        .addToAnnotations(AUTOTLS_ANNOTATION_KEY, secretName)
                        .endMetadata();
            }
        });
    }

    @Override
    public void enrich(PlatformMode platformMode, KubernetesListBuilder builder) {
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
