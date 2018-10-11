package io.fabric8.maven.enricher.api;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class DockerRegistryAuthentication {

    private String username;
    private String password;
    private String email;

    public DockerRegistryAuthentication() {
    }

    public DockerRegistryAuthentication(String username, String password, String email) {
        this.username = username;
        this.password = password;


        if (email == null || StringUtils.isBlank(email)) {
            email = "foo@foo.com";
        }

        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }

    public Map<String, String> asMap() {
        final Map<String, String> params = new HashMap<>();
        params.put("username", this.username);
        params.put("password", this.password);
        params.put("email", this.email);

        return params;
    }
}
