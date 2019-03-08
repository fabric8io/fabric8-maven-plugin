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
package io.fabric8.maven.core.util.kubernetes;

import io.fabric8.maven.core.util.AsciiDocParser;
import io.fabric8.maven.core.util.EnvUtil;
import io.fabric8.maven.core.util.PropertiesMappingParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class KindFilenameMapperUtil {

    public static Map<String, List<String>> loadMappings() {

        final String location = "/META-INF/fabric8/kind-filename-type-mapping-default.adoc";
        final String locationMappingProperties =
            EnvUtil.getEnvVarOrSystemProperty("fabric8.mapping", "/META-INF/fabric8/kind-filename-type-mapping-default.properties");

        try (final InputStream mappingFile = loadContent(location); final InputStream mappingPropertiesFile = loadContent(locationMappingProperties)) {
            final AsciiDocParser asciiDocParser = new AsciiDocParser();
            final Map<String, List<String>> defaultMapping = asciiDocParser.serializeKindFilenameTable(mappingFile);

            if (mappingPropertiesFile != null) {
                PropertiesMappingParser propertiesMappingParser = new PropertiesMappingParser();
                defaultMapping.putAll(propertiesMappingParser.parse(mappingPropertiesFile));
            }

            return defaultMapping;

        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static InputStream loadContent(String location) {
        InputStream resourceAsStream = KindFilenameMapperUtil.class.getResourceAsStream(location);

        if (resourceAsStream == null) {
            final File locationFile = new File(location);

            try {
                return new FileInputStream(locationFile);
            } catch (FileNotFoundException e) {
                return null;
            }
        }

        return resourceAsStream;
    }

}
