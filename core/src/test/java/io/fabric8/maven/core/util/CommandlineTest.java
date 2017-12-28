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
    public void simpleTest(){
        result = commandline.translateCommandline("cd /tmp");
        expected.clear();
        expected.add("cd");
        expected.add("/tmp");
        assertEquals(expected,result);
    }

    @Test
    public void simpleTestSecond(){
        result = commandline.translateCommandline("echo \"Hello! World\"");
        expected.clear();
        expected.add("echo");
        expected.add("Hello! World");
        assertEquals(expected,result);
    }

    @Test
    public void simpleTestThird(){
        result = commandline.
                translateCommandline("echo \"Hello! World\" \'Hello Java Folks\'");
        expected.clear();
        expected.add("echo");
        expected.add("Hello! World");
        expected.add("Hello Java Folks");
        assertEquals(expected,result);
    }

    @Test
    public void simpleTestFourth(){
        result = commandline.
                translateCommandline("echo \"Hello! World \'Hello Java Folks\'\"");
        expected.clear();
        expected.add("echo");
        expected.add("Hello! World \'Hello Java Folks\'");
        assertEquals(expected,result);
    }

    @Test
    public void simpleInvalidCommandTest(){
        try {
            result = commandline.
                    translateCommandline("echo \"Hello! World\" \'Hello Java Folks");
        }
        catch (IllegalArgumentException e){
            assertEquals("unbalanced quotes in echo \"Hello! World\" \'Hello Java Folks",
                    e.getMessage());
        }
    }
    @Test
    public void simpleInvalidCommandTestSecond(){
        try {
            result = commandline.
                    translateCommandline("echo \"Hello! World \'Hello Java Folks\'");
        }
        catch (IllegalArgumentException e){
            assertEquals("unbalanced quotes in echo \"Hello! World \'Hello Java Folks\'",
                    e.getMessage());
        }
    }
}