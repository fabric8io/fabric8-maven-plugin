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
package io.fabric8.maven.core.model;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author roland
 * @since 15.10.18
 */
public class GroupArtifactVersionTest {

    @Test
    public void checkSnapshot() {
        Object[] data = new Object[] {
            "1.0-SNAPSHOT", true,
            "1.2.3", false,
            "4.2-GA", false
        };
        for (int i = 0; i < data.length; i+=2) {
            GroupArtifactVersion gav = new GroupArtifactVersion("group", "artifact", (String) data[i]);
            assertThat(gav.isSnapshot()).isEqualTo(data[i+1]);
        }

    }
}
