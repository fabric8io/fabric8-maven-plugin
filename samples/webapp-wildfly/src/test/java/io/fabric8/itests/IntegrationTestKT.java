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