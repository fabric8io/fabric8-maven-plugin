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
package io.fabric8.maven.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class PropertiesMappingParser {

    /**
     * This method reads properties file to load custom mapping between kinds and filenames.
     *
     * <pre>
     * ConfigMap=cm, configmap
     * Service=service
     * </pre>
     *
     * @param mapping
     *     stream of a properties file setting mappings between kinds and filenames.
     *
     * @return Serialization of all elements as a map
     */
    public Map<String, List<String>> parse(final InputStream mapping) {

        final Properties mappingProperties = new Properties();
        try {
            mappingProperties.load(mapping);

            final Map<String, List<String>> serializedContent = new HashMap<>();

            final Set<String> kinds = mappingProperties.stringPropertyNames();

            for (String kind : kinds) {
                final String filenames = mappingProperties.getProperty(kind);
                final String[] filenameTypes = filenames.split(",");
                final List<String> scannedFiletypes = new ArrayList<>();
                for (final String filenameType : filenameTypes) {
                    scannedFiletypes.add(filenameType.trim());
                }
                serializedContent.put(kind, scannedFiletypes);
            }

            return serializedContent;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
