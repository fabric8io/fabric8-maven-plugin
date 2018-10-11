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

import java.util.HashMap;
import java.util.Map;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by yuwzho on 8/8/2017.
 */
public class DockerServerUtilTest {
    private final Settings settings = createSettings();

    private Settings createSettings() {
        Settings settings = new Settings();
        Server server = new Server();
        server.setId("docker.io");
        server.setUsername("username");
        server.setPassword("password");
        settings.addServer(server);

        Server server1 = new Server();
        server1.setId("docker1.io");
        server1.setUsername("username1");
        server1.setPassword("password1");
        Xpp3Dom mail = new Xpp3Dom("email");
        mail.setValue("bar@bar.com");
        Xpp3Dom configuration = new Xpp3Dom("configuration");
        configuration.addChild(mail);
        server1.setConfiguration(configuration);
        settings.addServer(server1);

        Server server2 = new Server();
        server2.setId("docker2.io");
        server2.setUsername("username2");
        server2.setPassword("password2");
        Xpp3Dom configuration1 = new Xpp3Dom("configuration");
        server2.setConfiguration(configuration1);
        settings.addServer(server2);
        return settings;
    }

    @Test
    public void testDockerUtilGetServerJson() {

        Map<String, String> params = new HashMap<>();
        params.put("username", "username");
        params.put("password", "password");

        String server = DockerServerUtil.getDockerJsonConfigString("docker.io", params);
        assertEquals("{\"docker.io\":{\"password\":\"password\",\"email\":\"foo@foo.com\",\"username\":\"username\"}}", server);

        Map<String, String> params1 = new HashMap<>();
        params1.put("username", "username1");
        params1.put("password", "password1");
        params1.put("email", "bar@bar.com");

        String server1 = DockerServerUtil.getDockerJsonConfigString("docker1.io", params1);
        assertEquals("{\"docker1.io\":{\"password\":\"password1\",\"email\":\"bar@bar.com\",\"username\":\"username1\"}}", server1);

        String server2 = DockerServerUtil.getDockerJsonConfigString("docker.io", null);
        assertEquals("",server2);

    }
}
