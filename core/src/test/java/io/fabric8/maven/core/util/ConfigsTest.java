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

import mockit.Mocked;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Test;
import mockit.Expectations;

import java.util.Properties;

import static org.junit.Assert.*;

public class ConfigsTest {

    public static final String KEY_1 = "key1";
    public static final String KEY_2 = "key2";
    public static final String KEY_3 = "key3";

    String value="value";

    @Mocked
    Properties properties;

    @Test
    public void getIntValueTest(){
        int result = Configs.asInt("85");
        assertEquals(85,result);

        result = Configs.asInt(null);
        assertEquals(0,result);

        try{
            result = Configs.asInt("parse");
        }
        catch (Exception e){
            assertEquals("For input string: \"parse\"",e.getMessage());
        }
    }

    @Test
    public void getBooleanValueTest(){
        boolean result = Configs.asBoolean("85");
        assertEquals(false,result);

        result = Configs.asBoolean(null);
        assertEquals(false,result);

        result = Configs.asBoolean("false");
        assertEquals(false,result);

        result = Configs.asBoolean("true");
        assertEquals(true,result);

        result = Configs.asBoolean("0");
        assertEquals(false,result);

        result = Configs.asBoolean("1");
        assertEquals(false,result);

    }

    @Test
    public void getStringValueTest(){
        String test = RandomStringUtils.randomAlphabetic(10);
        assertEquals(test,Configs.asString(test));
    }

    @Test
    public void getPropertyValueTest(){

        new Expectations() {{
            properties.getProperty(KEY_1);
            result = value;

            properties.getProperty(KEY_2);
            result = null;

            System.getProperty(KEY_2);
            result = value;

            properties.getProperty(KEY_3);
            result = null;

            System.getProperty(KEY_3);
            result = null;
        }};


        assertEquals("value",Configs.getPropertyWithSystemAsFallback(properties, KEY_1));
        assertEquals("value",Configs.getPropertyWithSystemAsFallback(properties, KEY_2));
        assertEquals(null,Configs.getPropertyWithSystemAsFallback(properties,KEY_3));
    }
}