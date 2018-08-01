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

    public static String getDockerJsonConfigString(final Settings settings, final String serverId) {
        Server server = getServer(settings, serverId);
        if (server == null) {
            return new String();
        }

        Map<String, String> auth = new HashMap();
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
