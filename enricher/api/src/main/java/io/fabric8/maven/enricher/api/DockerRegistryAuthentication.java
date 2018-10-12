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
