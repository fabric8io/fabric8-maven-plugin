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

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

import javax.validation.ConstraintViolationException;

import com.jayway.jsonassert.impl.matcher.IsCollectionWithSize;
import io.fabric8.maven.core.util.validator.ResourceValidator;
import io.fabric8.maven.docker.util.Logger;
import mockit.Mocked;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ResourceValidatorTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Mocked
    private Logger logger;

    @Test
    public void testValidKubernetesResources() throws IOException, URISyntaxException {
        // Given
        URL fileUrl = ResourceValidatorTest.class.getResource("/validations/kubernetes-deploy.yml");

        // When
        ResourceValidator resourceValidator = new ResourceValidator(Paths.get(fileUrl.toURI()).toFile(), ResourceClassifier.KUBERNETES, logger);
        int validResources = resourceValidator.validate();

        // Then
        Assert.assertEquals(1, validResources);
    }

    @Test
    public void testInvalidKubernetesPodSpec() throws IOException, URISyntaxException {
        // Given
        URL fileUrl = ResourceValidatorTest.class.getResource("/validations/kubernetes-deploy-invalid.yml");

        // When
        ResourceValidator resourceValidator = new ResourceValidator(Paths.get(fileUrl.toURI()).toFile(), ResourceClassifier.KUBERNETES, logger);

        // Then
        thrown.expect(ConstraintViolationException.class);
        thrown.expect(Matchers.hasProperty("constraintViolations", IsCollectionWithSize.hasSize(2)));

        // On
        resourceValidator.validate();
    }

    @Test
    public void testValidKubernetesResourcesDirectory() throws IOException, URISyntaxException {
        // Given
        URL fileUrl = ResourceValidatorTest.class.getResource("/validations/kubernetes");

        // When
        ResourceValidator resourceValidator = new ResourceValidator(Paths.get(fileUrl.toURI()).toFile(), ResourceClassifier.KUBERNETES, logger);
        int resources = resourceValidator.validate();

        // Then
        Assert.assertEquals(2, resources);
    }

    @Test
    public void testValidOpenShiftResources() throws IOException, URISyntaxException {
        // Given
        URL fileUrl = ResourceValidatorTest.class.getResource("/validations/openshift-deploymentconfig.yml");

        // When
        ResourceValidator resourceValidator = new ResourceValidator(Paths.get(fileUrl.toURI()).toFile(), ResourceClassifier.OPENSHIFT, logger);
        int resources = resourceValidator.validate();

        // Then
        Assert.assertEquals(1, resources);
    }

    @Test
    public void testInvalidOpenshiftDeployConfig() throws IOException, URISyntaxException {
        // Given
        URL fileUrl = ResourceValidatorTest.class.getResource("/validations/openshift-invalid-deploymentconfig.yml");

        // When
        ResourceValidator resourceValidator = new ResourceValidator(Paths.get(fileUrl.toURI()).toFile(), ResourceClassifier.OPENSHIFT, logger);

        // Then
        thrown.expect(ConstraintViolationException.class);
        thrown.expect(Matchers.hasProperty("constraintViolations", IsCollectionWithSize.hasSize(1)));

        // On
        resourceValidator.validate();
    }

    @Test
    public void testValidOpenshiftResourcesDirectory() throws IOException, URISyntaxException {
        // Given
        URL fileUrl = ResourceValidatorTest.class.getResource("/validations/openshift");

        // When
        ResourceValidator resourceValidator = new ResourceValidator(Paths.get(fileUrl.toURI()).toFile(), ResourceClassifier.OPENSHIFT, logger);
        int resources = resourceValidator.validate();

        // Then
        Assert.assertEquals(2, resources);
    }
}
