/*
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
package io.fabric8.maven.enricher.fabric8;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MapUtil;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;

public class CamelRestDSLEnricher extends BaseEnricher {
    static final String ENRICHER_NAME        = "f8-camel-dsl";

    //Default Domain for Service Annotations
    static final String DEFAULT_DOMAIN       = "api.service.kubernetes.io";

    //Kubernetes Service Annotations
    static final String SCHEME               = "scheme";
    static final String PROTOCOL             = "protocol";
    static final String PATH                 = "path";
    static final String DESCRIPTION_LANGUAGE = "description-language";
    static final String DESCRIPTION_PATH     = "description-path";

    private File springConfigDir;
    private String domain;

    private enum Config implements Configs.Key {
        springDir,
        annotationsDomain;
  
        public String def() { return d; } protected String d;
    }

    public CamelRestDSLEnricher(EnricherContext buildContext) {
        super(buildContext, ENRICHER_NAME);

        String baseDir  = getProject().getBasedir().getAbsolutePath();
        springConfigDir = new File(getConfig(Config.springDir, baseDir + "/src/main/resources/spring"));
        domain          = getConfig(Config.annotationsDomain, DEFAULT_DOMAIN) + "/";
    }

    @Override
    public Map<String, String> getAnnotations(Kind kind) {

        if (kind == Kind.SERVICE) {
            if (isCamelRestDslProject()) {
                Map<String, String> annotations = new HashMap<>();
                MapUtil.putIfAbsent(annotations, domain + SCHEME              , "http");
                MapUtil.putIfAbsent(annotations, domain + PROTOCOL            , "REST");
                MapUtil.putIfAbsent(annotations, domain + PATH                , "/");
                MapUtil.putIfAbsent(annotations, domain + DESCRIPTION_LANGUAGE, "OpenAPI");
                MapUtil.putIfAbsent(annotations, domain + DESCRIPTION_PATH    , "/openapi.json");
                if (log.isVerboseEnabled()) {
                    for (String annotationName : annotations.keySet()) {
                        log.verbose("Add service.kubernetes.io annotation: %s=%s",
                                annotationName, annotations.get(annotationName));
                    }
                }
                return annotations;
            }
        }

        return super.getAnnotations(kind);
    }

    private boolean isCamelRestDslProject() {

        File camelContextXml = new File(springConfigDir.getAbsoluteFile() + "/camel-context.xml");
        if (camelContextXml.exists()) {
            try {
                String content = new String(Files.readAllBytes(Paths.get(camelContextXml.toURI())));
                if (content.toLowerCase().contains("<rest path=")) return true;
            } catch (Exception e) {
                log.error("Failed to load camel context file: %s", e);
            }
        }
        return false;
    }

    public String getDomain() {
        return domain;
    }
}
