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

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IoUtilTest {

    @Test
    public void findOpenPort() throws IOException {
        int port = IoUtil.getFreeRandomPort();
        try (ServerSocket ss = new ServerSocket(port)) {
        }
    }

    @Test
    public void findOpenPortWhenPortsAreBusy() throws IOException {
        int port = IoUtil.getFreeRandomPort(49152, 60000, 100);
        try (ServerSocket ss = new ServerSocket(port)) {
        }
        int port2 = IoUtil.getFreeRandomPort(port, 65535, 100);
        try (ServerSocket ss = new ServerSocket(port2)) {
        }
        assertTrue(port2 > port);
    }

    @Test
    public void testSanitizeFileName() {
        assertEquals(null, IoUtil.sanitizeFileName(null));
        assertEquals("Hello-World", IoUtil.sanitizeFileName("Hello/&%World"));
        assertEquals("-H-e-l-l-o-", IoUtil.sanitizeFileName(" _H-.-e-.-l-l--.//o()"));
        assertEquals("s2i-env-docker-io-fabric8-java-latest", IoUtil.sanitizeFileName("s2i-env-docker.io/fabric8/java:latest"));
    }

}
