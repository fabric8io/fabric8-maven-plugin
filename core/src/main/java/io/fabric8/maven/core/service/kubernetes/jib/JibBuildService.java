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
package io.fabric8.maven.core.service.kubernetes.jib;

import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.core.service.Fabric8ServiceException;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.util.Logger;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

import java.util.Objects;

@Component(role = JibBuildService.class)
public class JibBuildService implements BuildService {

    @Requirement
    JibAssemblyManager jibAssemblyManager;

    private BuildServiceConfig config;

    private Logger log;

    public JibBuildService(BuildServiceConfig config, JibAssemblyManager jibAssemblyManager, Logger log) {
        Objects.requireNonNull(config, "config");
        this.config = config;
        this.log = log;
        this.jibAssemblyManager = jibAssemblyManager;
    }

    @Override
    public void build(ImageConfiguration imageConfiguration) throws Fabric8ServiceException {
       try {
           JibServiceUtil.buildImage(config.getDockerMojoParameters(), imageConfiguration, jibAssemblyManager, log);
       } catch (Exception ex) {
           throw new Fabric8ServiceException("Error when building JIB image", ex);
       }
    }

    @Override
    public void postProcess(BuildServiceConfig config) {

    }
}
