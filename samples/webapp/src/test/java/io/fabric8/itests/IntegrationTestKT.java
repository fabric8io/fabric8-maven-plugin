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
package io.fabric8.itests;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.fabric8.kubernetes.assertions.Assertions.assertThat;

/**
 * Tests that the Kubernetes resources (Services, Replication Controllers and
 * Pods) can be provisioned and start up correctly.
 *
 * This test creates a new Kubernetes Namespace for the duration of the test.
 * For more information see: http://fabric8.io/guide/testing.html
 */
@RunWith(Arquillian.class)
public class IntegrationTestKT {

	@ArquillianResource
	protected KubernetesClient kubernetes;

	@Test
	public void testRunningPodStaysUp() throws Exception {
		assertThat(kubernetes).deployments().pods().isPodReadyForPeriod();
	}
}