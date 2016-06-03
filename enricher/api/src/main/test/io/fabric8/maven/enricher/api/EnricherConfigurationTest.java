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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author roland
 * @since 03/06/16
 */
public class EnricherConfigurationTest {

    private enum Config implements EnricherConfiguration.ConfigKey {
        type {
            public String defVal() {
                return null;
            }
        }
    }

    @Test
    public void simple() throws Exception {
        Map<String,String> configMap = new HashMap<>();
        configMap.put("default.service.type", "LoadBalancer");
        EnricherConfiguration config = new EnricherConfiguration("default.service",configMap);
        assertEquals("LoadBalancer",config.get(Config.type));
    }
}
