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
package io.fabric8.maven.core.config;

import java.util.Map;

public class OpenshiftBuildConfig {
    private Map<String, String> limits;
    private Map<String, String> requests;

    public OpenshiftBuildConfig(Map<String, String> limits, Map<String, String> requests) {
        this.limits = limits;
        this.requests = requests;
    }

    public Map<String, String> getRequests() {
        return requests;
    }

    public void setRequests(Map<String, String> requests) {
        this.requests = requests;
    }

    public Map<String, String> getLimits() {
        return limits;
    }

    public void setLimits(Map<String, String> resourceLimits) {
        this.limits = resourceLimits;
    }
}
