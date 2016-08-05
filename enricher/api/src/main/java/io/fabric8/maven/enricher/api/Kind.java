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

package io.fabric8.maven.enricher.api;
/**
 * Enum describing the object types which are created
 *
 * @author roland
 * @since 07/04/16
 */
public enum Kind {
    SERVICE,
    REPLICA_SET,
    REPLICATION_CONTROLLER,
    DEPLOYMENT,
    DEPLOYMENT_CONFIG,
    POD_SPEC;

    /**
     * Returns true if the kind is a Deployment/DeploymentConfig or ReplicaSet/ReplicationController
     */
    public boolean isDeployOrReplicaKind() {
        return this == Kind.REPLICA_SET || this == Kind.REPLICATION_CONTROLLER ||
               this == Kind.DEPLOYMENT || this == Kind.DEPLOYMENT_CONFIG;
    }

    /**
     * Check whether kinds is a Service
     *
     * @param kind kind to check
     * @return true if the given kind is a service
     */
    public boolean isService() {
        return this == Kind.SERVICE;
    }
}