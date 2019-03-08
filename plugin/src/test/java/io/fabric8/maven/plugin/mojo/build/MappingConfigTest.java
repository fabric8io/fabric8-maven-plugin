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
package io.fabric8.maven.plugin.mojo.build;

import java.io.File;
import org.apache.maven.plugin.testing.MojoRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

@Ignore("I have not been able to make it run due classpath problems. "
    + "I have tried to fix but I have not find any way to ignore the conflict library from all transitive dependencies."
    + "conflict with plexus-io is the root cause coming as dependency from DMP plugin. But excluding it is not an option"
    + "since it is used too.")
public class MappingConfigTest {

    @Rule
    public MojoRule rule = new MojoRule();

    private final static String BASIC_TEST_FILE_PATH = "src/test/resources/mojo/build/mapping-mojo-config.xml";

    @Test
    public void testResourceMojoKindFilenameMappingsEmbeddedConfiguration() throws Exception {

        try {
            ResourceMojo mojo = getResourceMojo();
        } catch(Exception ex) {
            throw ex;
        }

    }

    private ResourceMojo getResourceMojo() throws Exception {
        File pluginConfig = new File( ".", BASIC_TEST_FILE_PATH );
        return( (ResourceMojo) this.rule.lookupMojo("resource", pluginConfig) );
    }

}
