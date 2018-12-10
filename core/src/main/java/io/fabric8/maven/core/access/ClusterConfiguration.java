package io.fabric8.maven.core.access;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.maven.core.util.kubernetes.KubernetesHelper;
import org.apache.commons.lang3.StringUtils;

public class ClusterConfiguration {

    private String masterUrl;
    private String apiVersion;
    private String namespace;
    private String caCertFile;
    private String caCertData;
    private String clientCertFile;
    private String clientCertData;
    private String clientKeyFile;
    private String clientKeyData;
    private String clientKeyAlgo;
    private String clientKeyPassphrase;
    private String trustStoreFile;
    private String trustStorePassphrase;
    private String keyStoreFile;
    private String keyStorePassphrase;

    public ClusterConfiguration() {
    }

    public void setMasterUrl(String masterUrl) {
        this.masterUrl = masterUrl;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public void setNamespace(String ns) {
        if (StringUtils.isBlank(ns)) {
            ns = KubernetesHelper.getDefaultNamespace();
        }

        this.namespace = ns;
    }

    public void setCaCertFile(String caCertFile) {
        this.caCertFile = caCertFile;
    }

    public void setCaCertData(String caCertData) {
        this.caCertData = caCertData;
    }

    public void setClientCertFile(String clientCertFile) {
        this.clientCertFile = clientCertFile;
    }

    public void setClientCertData(String clientCertData) {
        this.clientCertData = clientCertData;
    }

    public void setClientKeyFile(String clientKeyFile) {
        this.clientKeyFile = clientKeyFile;
    }

    public void setClientKeyData(String clientKeyData) {
        this.clientKeyData = clientKeyData;
    }

    public void setClientKeyAlgo(String clientKeyAlgo) {
        this.clientKeyAlgo = clientKeyAlgo;
    }

    public void setClientKeyPassphrase(String clientKeyPassphrase) {
        this.clientKeyPassphrase = clientKeyPassphrase;
    }

    public void setTrustStoreFile(String trustStoreFile) {
        this.trustStoreFile = trustStoreFile;
    }

    public void setTrustStorePassphrase(String trustStorePassphrase) {
        this.trustStorePassphrase = trustStorePassphrase;
    }

    public void setKeyStoreFile(String keyStoreFile) {
        this.keyStoreFile = keyStoreFile;
    }

    public void setKeyStorePassphrase(String keyStorePassphrase) {
        this.keyStorePassphrase = keyStorePassphrase;
    }

    public String getNamespace() {
        return namespace;
    }

    public Config getConfig() {
        final ConfigBuilder configBuilder = new ConfigBuilder();

        if (StringUtils.isNotBlank(this.masterUrl)) {
            configBuilder.withMasterUrl(this.masterUrl);
        }

        if (StringUtils.isNotBlank(this.apiVersion)) {
            configBuilder.withApiVersion(this.apiVersion);
        }

        if (StringUtils.isNotBlank(this.caCertData)) {
            configBuilder.withCaCertData(this.caCertData);
        }

        if (StringUtils.isNotBlank(this.caCertFile)) {
            configBuilder.withCaCertFile(this.caCertFile);
        }

        if (StringUtils.isNotBlank(this.clientCertData)) {
            configBuilder.withClientCertData(this.clientCertData);
        }

        if (StringUtils.isNotBlank(this.clientCertFile)) {
            configBuilder.withClientCertFile(this.clientCertFile);
        }

        if (StringUtils.isNotBlank(this.clientKeyAlgo)) {
            configBuilder.withClientKeyAlgo(this.clientKeyAlgo);
        }

        if (StringUtils.isNotBlank(this.clientKeyData)) {
            configBuilder.withClientKeyData(this.clientKeyData);
        }

        if (StringUtils.isNotBlank(this.clientKeyFile)) {
            configBuilder.withClientKeyFile(this.clientKeyFile);
        }

        if (StringUtils.isNotBlank(this.clientKeyPassphrase)) {
            configBuilder.withClientKeyPassphrase(this.clientKeyPassphrase);
        }

        if (StringUtils.isNotBlank(this.keyStoreFile)) {
            configBuilder.withKeyStoreFile(this.keyStoreFile);
        }

        if (StringUtils.isNotBlank(this.keyStorePassphrase)) {
            configBuilder.withKeyStorePassphrase(this.keyStorePassphrase);
        }

        if (StringUtils.isNotBlank(this.namespace)) {
            configBuilder.withNamespace(this.namespace);
        }

        if (StringUtils.isNotBlank(this.trustStoreFile)) {
            configBuilder.withTrustStoreFile(this.trustStoreFile);
        }

        if (StringUtils.isNotBlank(this.trustStorePassphrase)) {
            configBuilder.withTrustStorePassphrase(this.trustStorePassphrase);
        }

        return configBuilder.build();

    }
}
