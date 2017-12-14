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

package io.fabric8.maven.rt;

import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;

/**
 * Created by hshinde on 11/24/17.
 */

public class RouteAssert {

    private RouteAssert() {

    }

    public static void assertRoute(OpenShiftClient client, String appName) {
        for (Route route : client.routes().list().getItems()) {
            if (route.getMetadata().getName().equalsIgnoreCase(appName)) {
                return;
            }
        }

        throw new AssertionError("[No route exists for name: " + appName + "] " +
                "Expecting actual not to be null");
    }
}
