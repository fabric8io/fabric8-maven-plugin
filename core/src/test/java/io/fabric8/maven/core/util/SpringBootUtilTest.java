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

import java.util.Properties;

import org.junit.Test;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Checking the behaviour of utility methods.
 */
public class SpringBootUtilTest {


    @Test
    public void testYamlToPropertiesParsing() {

        Properties props = YamlUtil.getPropertiesFromYamlResource(SpringBootUtilTest.class.getResource("/util/test-application.yml"));
        assertNotEquals(0, props.size());

        assertEquals("8081", props.getProperty("management.port"));
        assertEquals("jdbc:mysql://127.0.0.1:3306", props.getProperty("spring.datasource.url"));
        assertEquals("value0", props.getProperty("example.nested.items[0].value"));
        assertEquals("value1", props.getProperty("example.nested.items[1].value"));
        assertEquals("sub0", props.getProperty("example.nested.items[2].elements[0].element[0].subelement"));
        assertEquals("sub1", props.getProperty("example.nested.items[2].elements[0].element[1].subelement"));
        assertEquals("integerKeyElement", props.getProperty("example.1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidFileThrowsException() {
        YamlUtil.getPropertiesFromYamlResource(SpringBootUtilTest.class.getResource("/util/invalid-application.yml"));
    }

    @Test
    public void testNonExistentYamlToPropertiesParsing() {

        Properties props = YamlUtil.getPropertiesFromYamlResource(SpringBootUtilTest.class.getResource("/this-file-does-not-exist"));
        assertNotNull(props);
        assertEquals(0, props.size());

    }

    @Test
    public void testPropertiesParsing() {

        Properties props = SpringBootUtil.getPropertiesResource(SpringBootUtilTest.class.getResource("/util/test-application.properties"));
        assertNotEquals(0, props.size());

        assertEquals("8081", props.getProperty("management.port"));
        assertEquals("jdbc:mysql://127.0.0.1:3306", props.getProperty("spring.datasource.url"));
        assertEquals("value0", props.getProperty("example.nested.items[0].value"));
        assertEquals("value1", props.getProperty("example.nested.items[1].value"));

    }

    @Test
    public void testNonExistentPropertiesParsing() {

        Properties props = SpringBootUtil.getPropertiesResource(SpringBootUtilTest.class.getResource("/this-file-does-not-exist"));
        assertNotNull(props);
        assertEquals(0, props.size());

    }

    @Test
    public void testMultipleProfilesParsing() {
        Properties props = SpringBootUtil.getPropertiesFromApplicationYamlResource(null, getClass().getResource("/util/test-application-with-multiple-profiles.yml"));
        assertTrue(props.size() > 0);

        assertEquals("spring-boot-k8-recipes", props.get("spring.application.name"));
        assertEquals("false", props.get("management.endpoints.enabled-by-default"));
        assertEquals("true", props.get("management.endpoint.health.enabled"));
        assertNull(props.get("cloud.kubernetes.reload.enabled"));

        props = SpringBootUtil.getPropertiesFromApplicationYamlResource("kubernetes", getClass().getResource("/util/test-application-with-multiple-profiles.yml"));
        assertEquals("true", props.get("cloud.kubernetes.reload.enabled"));
        assertNull(props.get("spring.application.name"));
    }

}
