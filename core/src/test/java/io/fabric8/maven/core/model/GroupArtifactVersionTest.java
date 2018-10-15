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
