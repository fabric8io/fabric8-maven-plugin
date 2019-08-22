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
package io.fabric8.maven.sample.enricher.secret;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.MavenEnricherContext;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import io.fabric8.maven.core.config.PlatformMode;

/**
 * @author roland
 * @since 10.04.17
 */
public class SecretEnricher extends BaseEnricher {

    public SecretEnricher(MavenEnricherContext buildContext) {
        super(buildContext, "secret-enricher");
    }

    // Available configuration keys
    private enum Config implements Configs.Key {
        // name of the secret to create
        name,

        // Whether to do base64 encoding
        encode {{ d = "true"; }};

        public String def() { return d; } protected String d;
    }

    @Override
    public void enrich(PlatformMode platformMode, KubernetesListBuilder builder) {
        Map<String, String> config = getRawConfig();
        SecretBuilder secretBuilder = createSecretBuilder();
        for (Map.Entry<String, String> entry : config.entrySet()) {
            if (!isTypedKey(entry.getKey())) {
                addToSecretBuilder(secretBuilder, entry.getKey(), entry.getValue());
            }
        }
        if (secretBuilder.hasData() && secretBuilder.getData().size() > 0) {
            builder.addToSecretItems(secretBuilder.build());
        }
    }

    private SecretBuilder createSecretBuilder() {
        return  new SecretBuilder()
                .withNewMetadata()
                .withName(getSecretName())
                .endMetadata();
    }

    private String getSecretName() {
        return getConfig(Config.name,
                         MavenUtil.createDefaultResourceName("custom-enricher"));
    }

    private void addToSecretBuilder(SecretBuilder builder, String name, String secretRef) {
        byte[] secret;
        try {
            secret = IOUtils.toByteArray(new URL(secretRef).openStream());
        } catch (MalformedURLException exp) {
            secret = secretRef.getBytes();
        } catch (IOException e) {
            log.error("Cannot read %s for creating secret %s: %s. Ignoring ...", secretRef, getSecretName(), e.getMessage());
            return;
        }

        if (Boolean.parseBoolean(getConfig(Config.encode))) {
            builder.addToData(name, Base64.encodeBase64String(secret));
        } else {
            builder.addToData(name, new String(secret));
        }
    }


    private boolean isTypedKey(String key) {
        for (Config cfg : Config.values()) {
            if (cfg.name().equals(key)) {
                return true;
            }
        }
        return false;
    }
}