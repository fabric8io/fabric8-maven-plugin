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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.config.Profile;
import io.fabric8.maven.core.util.ProfileUtil;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author roland
 * @since 24/07/16
 */
public class ProfileUtilTest {

    @Test
    public void simple() throws IOException {
        InputStream is = getClass().getResourceAsStream("/fabric8/config/profiles.yaml");
        assertNotNull(is);
        List<Profile> profiles = ProfileUtil.fromYaml(is);
        assertNotNull(profiles);
        assertEquals(profiles.size(),1);
        Profile profile = profiles.get(0);
        assertEquals("simple", profile.getName());
        ProcessorConfig config = profile.getEnricherConfig();
        assertTrue(config.use("base"));
        assertFalse(config.use("blub"));
        config = profile.getGeneratorConfig();
        assertFalse(config.use("java.app"));
        assertTrue(config.use("spring.swarm"));
    }

    @Test
    public void multiple() throws IOException {
        InputStream is = getClass().getResourceAsStream("/fabric8/config/multiple-profiles.yml");
        assertNotNull(is);
        List<Profile> profiles = ProfileUtil.fromYaml(is);
        assertEquals(2,profiles.size());
    }

    @Test
    public void fromClasspath() throws IOException {
        Map<String,Profile> profiles = ProfileUtil.readAllFromClasspath();
        assertEquals(2, profiles.size());
        assertTrue(profiles.containsKey("one"));
        assertTrue(profiles.containsKey("second"));
    }

    @Test
    public void lookup() throws IOException, URISyntaxException {
        File dir = new File(getClass().getResource("/fabric8/config/profiles.yaml").toURI()).getParentFile();
        Profile profile = ProfileUtil.lookup("simple", dir);
        assertEquals("simple", profile.getName());
        assertEquals("http://jolokia.org", profile.getEnricherConfig().getConfig("base.url"));

        profile = ProfileUtil.lookup("one", dir);
        assertTrue(profile.getGeneratorConfig().use("foobar"));

        assertNull(ProfileUtil.lookup("three", dir));
    }

}
