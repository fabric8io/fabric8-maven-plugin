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
package io.fabric8.maven.core.util;

import java.util.Properties;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Checking the behaviour of utility methods.
 */
public class MavenUtilTest {


    @Test
    public void testYamlToPropertiesParsing() {

        Properties props = MavenUtil.getPropertiesFromYamlResource(MavenUtilTest.class.getResource("/fabric8/contains_kind.yml"));
        assertEquals("flipper", props.getProperty("metadata.name"));
        assertEquals("5", props.getProperty("spec.template.spec.containers[0].env[0].value"));

    }

    @Test
    public void testNonExistentYamlToPropertiesParsing() {

        Properties props = MavenUtil.getPropertiesFromYamlResource(MavenUtilTest.class.getResource("/this-file-does-not-exist"));
        assertNotNull(props);
        assertEquals(0, props.size());

    }

}
