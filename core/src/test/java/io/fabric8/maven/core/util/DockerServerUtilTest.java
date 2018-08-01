package io.fabric8.maven.core.util;

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
    public void testDockerUtilGetServer() {
        Server server = DockerServerUtil.getServer(settings, "docker.io");
        assertEquals("docker.io", server.getId());
        assertEquals("username", server.getUsername());
        assertEquals("password", server.getPassword());
    }

    @Test
    public void testDockerUtilGetServerJson() {
        String server = DockerServerUtil.getDockerJsonConfigString(settings, "docker.io");
        assertEquals("{\"docker.io\":{\"password\":\"password\",\"email\":\"foo@foo.com\",\"username\":\"username\"}}", server);

        String server1 = DockerServerUtil.getDockerJsonConfigString(settings, "docker1.io");
        assertEquals("{\"docker1.io\":{\"password\":\"password1\",\"email\":\"bar@bar.com\",\"username\":\"username1\"}}", server1);

        String server2 = DockerServerUtil.getDockerJsonConfigString(null,"docker.io");
        assertEquals("",server2);

        String server3 = DockerServerUtil.getDockerJsonConfigString(settings,"docker2.io");
        assertEquals("{\"docker2.io\":{\"password\":\"password2\",\"email\":\"foo@foo.com\",\"username\":\"username2\"}}",server3);
    }
}
