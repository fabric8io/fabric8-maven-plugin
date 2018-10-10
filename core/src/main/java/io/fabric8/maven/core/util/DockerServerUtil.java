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
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.json.JSONObject;

public class DockerServerUtil {
    public static Server getServer(final Settings settings, final String serverId) {
        if (settings == null || StringUtils.isBlank(serverId)) {
            return null;
        }
        return settings.getServer(serverId);
    }

    public static String getDockerJsonConfigString(final String serverId, final Map<String, String> auth) {

        if (auth == null || auth.isEmpty()) {
            return "";
        }


        if (!auth.containsKey("email") || StringUtils.isBlank(auth.get("email"))) {
            auth.put("email", "foo@foo.com");
        }

        JSONObject json = new JSONObject()
                .put(serverId, auth);
        return json.toString();
    }

    //Method used in MOJO
    public static String getDockerJsonConfigString(final Settings settings, final String serverId) {
        Server server = getServer(settings, serverId);
        if (server == null) {
            return new String();
        }

        Map<String, String> auth = new HashMap<>();
        auth.put("username", server.getUsername());
        auth.put("password", server.getPassword());

        String mail = getConfigurationValue(server, "email");
        if (StringUtils.isBlank(mail)) {
            mail = "foo@foo.com";
        }
        auth.put("email", mail);

        JSONObject json = new JSONObject()
            .put(serverId, auth);
        return json.toString();
    }

    private static String getConfigurationValue(final Server server, final String key) {

        final Xpp3Dom configuration = (Xpp3Dom) server.getConfiguration();
        if (configuration == null) {
            return null;
        }

        final Xpp3Dom node = configuration.getChild(key);
        if (node == null) {
            return null;
        }

        return node.getValue();
    }

}
