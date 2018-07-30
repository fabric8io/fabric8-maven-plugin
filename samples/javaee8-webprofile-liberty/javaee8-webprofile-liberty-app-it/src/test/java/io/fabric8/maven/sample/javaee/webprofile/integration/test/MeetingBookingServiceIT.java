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
package io.fabric8.maven.sample.javaee.webprofile.integration.test;

import static org.junit.Assert.assertEquals;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import org.junit.Before;
import org.junit.Test;

public class MeetingBookingServiceIT {

	private static final String APPSERVER_TEST_HOST_PROPERTY = "appserver.test.host";
	
	private static final String APPSERVER_TEST_PORT_PROPERTY = "appserver.test.port";
	
	private static final String APPLICATION_CONTEXT_ROOT_PROPERTY = "application.context.root";
	
	private static final String HOST = "host";
	
	private static final String PORT = "port";
	
	private static final String CONTEXTROOT = "context-root";
	
	private static final String PROXY_URL_FMT = "http://{host}:{port}/{context-root}";
	
	private WebTarget target;
	
	@Before
	public void prepareWebTarget() {
		target = ClientBuilder.newClient().target(PROXY_URL_FMT)
				.resolveTemplate(HOST, System.getProperty(APPSERVER_TEST_HOST_PROPERTY))
				.resolveTemplate(PORT, System.getProperty(APPSERVER_TEST_PORT_PROPERTY))
				.resolveTemplate(CONTEXTROOT, System.getProperty(APPLICATION_CONTEXT_ROOT_PROPERTY));
	}
	
	@Test
	public void testBaseline() throws Exception {
        String banner = target.path("/")
        		.request()
        		.accept("text/html")
        		.get(String.class);
        assertEquals("Microservice Meeting Room Booking API Application", banner);        
	}
}
