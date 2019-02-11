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

import com.google.common.base.Objects;

import java.util.Properties;

/**
 * Mode how to interact with Kubernetes/Openshift cluster.
 *
 * @author roland
 * @since 25/05/16
 */
public enum RuntimeMode {

    /**
     * Create resources descriptors for vanilla Kubernetes
     */
    kubernetes(false, "Kubernetes"),

    /**
     * Use special OpenShift features like BuildConfigs
     */
    openshift(false, "OpenShift"),

    /**
     * Detect automatically whether running on OpenShift or Kuberentes.
     * This is done by contacting an API server
     */
    auto(true, "Auto");

    public static final RuntimeMode DEFAULT = RuntimeMode.auto;
    public static final String FABRIC8_EFFECTIVE_PLATFORM_MODE = "fabric8.internal.effective.platform.mode";

    private boolean autoFlag;
    private String label;

    RuntimeMode(boolean autoFlag, String label) {
        this.autoFlag = autoFlag;
        this.label = label;
    }

    public boolean isAuto() {
        return autoFlag;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Returns true if the given maven properties indicate running in OpenShift platform mode
     */
    public static boolean isOpenShiftMode(Properties properties) {
        return properties == null ? false : Objects.equal(openshift.toString(), properties.getProperty(FABRIC8_EFFECTIVE_PLATFORM_MODE, ""));
    }
}
