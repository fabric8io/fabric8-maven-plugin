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
package io.fabric8.maven.generator.webapp.handler;

import io.fabric8.maven.core.config.PlatformMode;
import org.apache.maven.project.MavenProject;

import java.util.Arrays;
import java.util.List;

public class WarS2IHandler extends AbstractAppServerHandler {
    public WarS2IHandler(MavenProject project) {
        super("webapp", project);
    }

    @Override
    public boolean isApplicable() {
        if(PlatformMode.isOpenShiftMode(project.getProperties())) {
            return hasOneOf("**/META-INF/context.xml") || project.getPackaging().equals("war");
        } else {
            return false;
        }
    }

    @Override
    public String getFrom() {
        return imageLookup.getImageName("wildfly.upstream.s2i");
    }

    @Override
    public List<String> exposedPorts() {
        return Arrays.asList("8080");
    }

    @Override
    public String getDeploymentDir() {
        return "/wildfly/standalone/deployments";
    }

    @Override
    public String getCommand() {
        return "./s2i/bin/usage";
    }

    @Override
    public String getUser() {
        return "1001";
    }
}
