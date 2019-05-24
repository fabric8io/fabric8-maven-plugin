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

import java.util.Properties;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * Configuration for labels or annotations
 *
 * @author roland
 * @since 22/03/16
 */
public class MetaDataConfig {
    /**
     * Labels or annotations which should be applied to every object
     */
    @Parameter
    private Properties all;

    /**
     * Labels or annotation for a Pod within a controller or deployment
     */
    @Parameter
    private Properties pod;


    /**
     * Labels or annotations for replica sets (or replication controller)
     */
    @Parameter
    private Properties replicaSet;

    /**
     * Labels or annotation for services
     */
    @Parameter
    private Properties service;

    /**
     * Labels or annotations for deployment or deployment configs
     */
    @Parameter
    private Properties deployment;

    public Properties getPod() {
        return pod;
    }

    public Properties getReplicaSet() {
        return replicaSet;
    }

    public Properties getService() {
        return service;
    }

    public Properties getAll() {
        return all;
    }

    public Properties getDeployment() {
        return deployment;
    }
}
