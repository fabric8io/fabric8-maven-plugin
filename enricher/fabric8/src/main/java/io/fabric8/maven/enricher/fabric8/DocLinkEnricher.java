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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import io.fabric8.maven.core.util.kubernetes.Fabric8Annotations;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.maven.enricher.api.Kind;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Site;
import org.apache.maven.project.MavenProject;

/**
 * Adds a link to the generated documentation for this microservice so we can link to the versioned docs in the
 * annotations
 */
public class DocLinkEnricher extends AbstractLiveEnricher {

    public DocLinkEnricher(EnricherContext buildContext) {
        super(buildContext, "f8-cd-doc-link");
    }

    @Override
    public Map<String, String> getAnnotations(Kind kind) {
        if (kind.isController()) {
            String url = findDocumentationUrl();
            return url != null ? Collections.singletonMap(Fabric8Annotations.DOCS_URL.value(), url) : null;
        } else {
            return null;
        }
    }

    protected String findDocumentationUrl() {
        DistributionManagement distributionManagement = findProjectDistributionManagement();
        if (distributionManagement != null) {
            Site site = distributionManagement.getSite();
            if (site != null) {
                String url = site.getUrl();
                if (StringUtils.isNotBlank(url)) {
                    // lets replace any properties...
                    MavenProject project = getProject();
                    if (project != null) {
                        url = replaceProperties(url, project.getProperties());
                    }

                    // lets convert the internal dns name to a public name
                    try {
                        String urlToParse = url;
                        int idx = url.indexOf("://");
                        if (idx > 0) {
                            // lets strip any previous schemes such as "dav:"
                            int idx2 = url.substring(0, idx).lastIndexOf(':');
                            if (idx2 >= 0 && idx2 < idx) {
                                urlToParse = url.substring(idx2 + 1);
                            }
                        }
                        URL u = new URL(urlToParse);
                        String serviceName = u.getHost();
                        String protocol = u.getProtocol();
                        if (isOnline()) {
                            // lets see if the host name is a service name in which case we'll resolve to the public URL
                            String publicUrl = getExternalServiceURL(serviceName, protocol);
                            if (StringUtils.isNotBlank(publicUrl)) {
                                return String.format("%s/%s",publicUrl, u.getPath());
                            }
                        }
                    } catch (MalformedURLException e) {
                        getLog().error("Failed to parse URL: %s. %s", url, e);
                    }
                    return url;
                }
            }
        }
        return null;
    }


    protected DistributionManagement findProjectDistributionManagement() {
        MavenProject project = getProject();
        while (project != null) {
            DistributionManagement distributionManagement = project.getDistributionManagement();
            if (distributionManagement != null) {
                return distributionManagement;
            }
            project = project.getParent();
        }
        return null;
    }

    /**
     * Replaces all text of the form <code>${foo}</code> with the value in the properties object
     */
    protected static String replaceProperties(String text, Properties properties) {
        Set<Map.Entry<Object, Object>> entries = properties.entrySet();
        for (Map.Entry<Object, Object> entry : entries) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (key != null && value != null) {
                String pattern = "${" + key + "}";
                text = StringUtils.replace(text, pattern, value.toString());
            }
        }
        return text;
    }
}
