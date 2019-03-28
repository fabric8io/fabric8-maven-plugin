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
package io.fabric8.maven.core.handler;

import io.fabric8.maven.core.model.GroupArtifactVersion;

import java.util.Properties;

/**
 * @author roland
 * @since 08/04/16
 */
public class HandlerHub {

    private final ServiceHandler serviceHandler;
    private final ReplicaSetHandler replicaSetHandler;
    private final ReplicationControllerHandler replicationControllerHandler;
    private final DeploymentHandler deploymentHandler;
    private final DeploymentConfigHandler deploymentConfigHandler;
    private final StatefulSetHandler statefulSetHandler;
    private final DaemonSetHandler daemonSetHandler;
    private final JobHandler jobHandler;


    public HandlerHub(GroupArtifactVersion groupArtifactVersion, Properties configuration) {
        ProbeHandler probeHandler = new ProbeHandler();
        ContainerHandler containerHandler = new ContainerHandler(configuration, groupArtifactVersion, probeHandler);
        PodTemplateHandler podTemplateHandler = new PodTemplateHandler(containerHandler);
    }

    public ServiceHandler getServiceHandler() {
        serviceHandler = new ServiceHandler();    	
        return serviceHandler;
    }

    public DeploymentHandler getDeploymentHandler() {
        deploymentHandler = new DeploymentHandler(podTemplateHandler);
        return deploymentHandler;
    }

    public DeploymentConfigHandler getDeploymentConfigHandler() {
        deploymentConfigHandler = new DeploymentConfigHandler(podTemplateHandler);
        return deploymentConfigHandler;
    }

    public ReplicaSetHandler getReplicaSetHandler() {
        replicaSetHandler = new ReplicaSetHandler(podTemplateHandler);
        return replicaSetHandler;
    }

    public ReplicationControllerHandler getReplicationControllerHandler() {
        replicationControllerHandler = new ReplicationControllerHandler(podTemplateHandler);
        return replicationControllerHandler;
    }

    public StatefulSetHandler getStatefulSetHandler() {
        statefulSetHandler = new StatefulSetHandler(podTemplateHandler);
        return statefulSetHandler;
    }

    public DaemonSetHandler getDaemonSetHandler() {
        daemonSetHandler = new DaemonSetHandler(podTemplateHandler);
        return daemonSetHandler;
    }

    public JobHandler getJobHandler() {
        jobHandler = new JobHandler(podTemplateHandler);
        return jobHandler;
    }
}
