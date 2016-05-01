package io.fabric8.maven.plugin.handler;
/*
 * 
 * Copyright 2016 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.fabric8.maven.plugin.util.EnricherManager;
import org.apache.maven.project.MavenProject;

/**
 * @author roland
 * @since 08/04/16
 */
public class HandlerHub {

    private final ServiceHandler serviceHandler;
    private final ReplicationControllerHandler replicationControllerHandler;

    public HandlerHub(MavenProject project, EnricherManager enricher) {
        LabelHandler labelHandler = new LabelHandler(enricher);
        ProbeHandler probeHandler = new ProbeHandler();
        EnvVarHandler envVarHandler = new EnvVarHandler(project);
        ContainerHandler containerHandler = new ContainerHandler(project, envVarHandler, probeHandler);
        PodTemplateHandler podTemplateHandler = new PodTemplateHandler(containerHandler, labelHandler);

        replicationControllerHandler = new ReplicationControllerHandler(podTemplateHandler, labelHandler);
        serviceHandler = new ServiceHandler(labelHandler);
    }

    public ServiceHandler getServiceHandler() {
        return serviceHandler;
    }

    public ReplicationControllerHandler getReplicationControllerHandler() {
        return replicationControllerHandler;
    }

}
