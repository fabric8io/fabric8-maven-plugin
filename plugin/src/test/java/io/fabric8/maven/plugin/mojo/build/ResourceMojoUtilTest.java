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

import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.maven.core.util.ProjectClassLoaders;
import io.fabric8.openshift.api.model.Template;
import mockit.Capturing;
import mockit.Expectations;
import mockit.Mocked;
import org.apache.maven.project.MavenProject;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ResourceMojoUtilTest {

    @Capturing
    private ProjectClassLoaders projectClassLoaders;
    @Mocked
    private MavenProject mockProject;

    @Test
    public void useDekorateHasDekorateInClassPathShouldReturnTrue() throws Exception {
        withMockProject();
        new Expectations() {{
            projectClassLoaders.isClassInCompileClasspath(true, "io.dekorate.annotation.Dekorate");
            result = true;
        }};
        final boolean result = ResourceMojoUtil.useDekorate(mockProject);
        assertTrue(result);
    }

    @Test
    public void useDekorateHasNotDekorateInClassPathShouldReturnFalse() throws Exception {
        withMockProject();
        new Expectations() {{
            projectClassLoaders.isClassInCompileClasspath(true, "io.dekorate.annotation.Dekorate");
            result = false;
        }};
        final boolean result = ResourceMojoUtil.useDekorate(mockProject);
        assertFalse(result);
    }

    @Test
    public void getSingletonTemplateShouldReturnSingleEntry(
        @Mocked KubernetesList mockList, @Mocked Template mockTemplate) {
        new Expectations() {{
            mockList.getItems();
            result = Collections.singletonList(mockTemplate);
        }};
        final Template result = ResourceMojoUtil.getSingletonTemplate(mockList);
        assertSame(mockTemplate, result);
    }

    private void withMockProject() throws Exception {
        new Expectations() {{
            mockProject.getCompileClasspathElements();
            result = Collections.emptyList();
            mockProject.getBuild().getOutputDirectory();
            result = "/";
        }};
    }
}
