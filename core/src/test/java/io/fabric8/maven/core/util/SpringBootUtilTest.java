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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Checking the behaviour of utility methods.
 */
public class SpringBootUtilTest {


    @Test
    public void testYamlToPropertiesParsing() {

        Properties props = SpringBootUtil.getPropertiesFromYamlResource(SpringBootUtilTest.class.getResource("/util/test-application.yml"));
        assertNotEquals(0, props.size());

        assertEquals("8081", props.getProperty("management.port"));
        assertEquals("jdbc:mysql://127.0.0.1:3306", props.getProperty("spring.datasource.url"));
        assertEquals("value0", props.getProperty("example.nested.items[0].value"));
        assertEquals("value1", props.getProperty("example.nested.items[1].value"));
        assertEquals("sub0", props.getProperty("example.nested.items[2].elements[0].element[0].subelement"));
        assertEquals("sub1", props.getProperty("example.nested.items[2].elements[0].element[1].subelement"));

    }

    @Test
    public void testNonExistentYamlToPropertiesParsing() {

        Properties props = SpringBootUtil.getPropertiesFromYamlResource(SpringBootUtilTest.class.getResource("/this-file-does-not-exist"));
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

}
