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

package io.fabric8.maven.enricher.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.util.Configs;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author roland
 * @since 03/06/16
 */
public class EnricherConfigTest {

    private enum Config implements Configs.Key {
        type;

        public String def() { return null; }
    }

    @Test
    public void simple() throws Exception {
        Map<String,TreeMap> configMap = new HashMap<>();
        TreeMap map = new TreeMap();
        map.put("type","LoadBalancer");
        configMap.put("default.service", map);
        EnricherConfig config = new EnricherConfig(new Properties(), "default.service", new ProcessorConfig(null, null, configMap));
        assertEquals("LoadBalancer",config.get(Config.type));
    }
}
