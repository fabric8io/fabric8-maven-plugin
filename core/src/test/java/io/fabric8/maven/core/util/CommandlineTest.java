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

import org.junit.Test;

import java.util.List;
import java.util.ArrayList;

import static org.junit.Assert.*;

public class CommandlineTest {

    Commandline commandline = new Commandline();
    List<String> result = new ArrayList<>();
    List<String> expected = new ArrayList<>();

    @Test
    public void simpleEmptyTest(){
        result = commandline.translateCommandline("");
        assertEquals(expected,result);
    }

    @Test
    public void simpleNullTest(){
        result = commandline.translateCommandline(null);
        assertEquals(expected,result);
    }

    @Test
    public void simpleCommandTest(){
        expected.clear();
        expected.add("cd");
        expected.add("/tmp");
        result = commandline.translateCommandline("cd /tmp");
        assertEquals(expected,result);
    }

    @Test
    public void CommandWithDoubleQuoteTest(){
        expected.clear();
        expected.add("echo");
        expected.add("Hello! World");
        result = commandline.translateCommandline("echo \"Hello! World\"");
        assertEquals(expected,result);
    }

    @Test
    public void commandWithBothTypeofQuotesTest(){
        expected.clear();
        expected.add("echo");
        expected.add("Hello! World");
        expected.add("Hello Java Folks");
        result = commandline.
                translateCommandline("echo \"Hello! World\" \'Hello Java Folks\'");
        assertEquals(expected,result);
    }

    @Test
    public void commandWithNestedQuotesTest(){
        expected.clear();
        expected.add("echo");
        expected.add("Hello! World \'Hello Java Folks\'");
        result = commandline.
                translateCommandline("echo \"Hello! World \'Hello Java Folks\'\"");
        assertEquals(expected,result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidDoubleQuoteCommandTest(){
        result = commandline.
                translateCommandline("echo \"Hello! World\" \'Hello Java Folks");
    }
    @Test(expected = IllegalArgumentException.class)
    public void invalidSingleQuoteCommandTest(){
        result = commandline.
                translateCommandline("echo \"Hello! World \'Hello Java Folks\'");
    }
}