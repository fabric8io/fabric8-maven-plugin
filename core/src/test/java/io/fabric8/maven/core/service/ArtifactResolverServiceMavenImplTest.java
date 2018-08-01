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
package io.fabric8.maven.core.service;

import java.io.File;
import java.util.Collections;

import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(JMockit.class)
public class ArtifactResolverServiceMavenImplTest {

    @Mocked
    private MavenProject mavenProject;

    @Mocked
    private RepositorySystem repositorySystem;

    @Mocked
    private ArtifactResolutionResult artifacts;

    @Mocked
    private Artifact artifact;

    @Before
    public void init() {
        new Expectations() {{

            artifact.getGroupId(); result = "groupid";
            artifact.getArtifactId(); result = "artifactid";
            artifact.getVersion(); result = "version";
            artifact.getType(); result = "type";
            artifact.getFile(); result = new File("dummy"); minTimes = 0;

            artifacts.isSuccess(); result = true;
            artifacts.getArtifacts(); result = Collections.singleton(artifact);

            repositorySystem.resolve(withInstanceOf(ArtifactResolutionRequest.class)); result = artifacts;
        }};
    }

    @Test
    public void testSuccessfulResolution() {
        ArtifactResolverService service = new ArtifactResolverServiceMavenImpl(repositorySystem, mavenProject);
        File file = service.resolveArtifact("groupid", "artifactid", "version", "type");
        assertNotNull(file);
        assertEquals("dummy", file.getName());
    }

    @Test(expected = IllegalStateException.class)
    public void testUnsuccessfulResolution() {
        ArtifactResolverService service = new ArtifactResolverServiceMavenImpl(repositorySystem, mavenProject);
        service.resolveArtifact("groupid", "artifactid", "version", "Anothertype");
    }

}
