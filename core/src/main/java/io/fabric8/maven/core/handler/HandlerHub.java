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

import io.fabric8.maven.core.model.Artifact;
import java.net.URLClassLoader;
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
    private final StatefulSetHandler statefulSetHandler;
    private final DaemonSetHandler daemonSetHandler;
    private final JobHandler jobHandler;

    public HandlerHub(URLClassLoader compileClassloader, String outputDirectory, Artifact artifact, Properties configuration) {
        ProbeHandler probeHandler = new ProbeHandler();
        EnvVarHandler envVarHandler = new EnvVarHandler(compileClassloader, outputDirectory);
        ContainerHandler containerHandler = new ContainerHandler(configuration, artifact, envVarHandler, probeHandler);
        PodTemplateHandler podTemplateHandler = new PodTemplateHandler(containerHandler);

        deploymentHandler = new DeploymentHandler(podTemplateHandler);
        replicaSetHandler = new ReplicaSetHandler(podTemplateHandler);
        replicationControllerHandler = new ReplicationControllerHandler(podTemplateHandler);
        serviceHandler = new ServiceHandler();
        statefulSetHandler = new StatefulSetHandler(podTemplateHandler);
        daemonSetHandler = new DaemonSetHandler(podTemplateHandler);
        jobHandler = new JobHandler(podTemplateHandler);
    }

    public ServiceHandler getServiceHandler() {
        return serviceHandler;
    }

    public DeploymentHandler getDeploymentHandler() {
        return deploymentHandler;
    }

    public ReplicaSetHandler getReplicaSetHandler() {
        return replicaSetHandler;
    }

    public ReplicationControllerHandler getReplicationControllerHandler() {
        return replicationControllerHandler;
    }

    public StatefulSetHandler getStatefulSetHandler() {
        return statefulSetHandler;
    }

    public DaemonSetHandler getDaemonSetHandler() {
        return daemonSetHandler;
    }

    public JobHandler getJobHandler() {
        return jobHandler;
    }
}
