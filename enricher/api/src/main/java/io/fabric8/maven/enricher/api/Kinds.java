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
 */
public class Kinds {
    /**
     * Returns true if the kind is a Deployment/DeploymentConfig or ReplicaSet/ReplicationController
     */
    public static boolean isDeployOrReplicaKind(Kind kind) {
        return kind == Kind.REPLICA_SET || kind == Kind.REPLICATION_CONTROLLER ||
                kind == Kind.DEPLOYMENT || kind == Kind.DEPLOYMENT_CONFIG;
    }
}
