/*
 * Copyright 2018 Red Hat, Inc.
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
package io.fabric8.maven.generator.thorntail.v2;

import java.util.List;
import java.util.Map;

import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.generator.api.GeneratorContext;
import io.fabric8.maven.generator.javaexec.JavaExecGenerator;
import org.apache.maven.plugin.MojoExecutionException;

public class ThorntailV2Generator extends JavaExecGenerator {

    public ThorntailV2Generator(GeneratorContext context) {
        super(context, "thorntail-v2");
    }

    @Override
    public boolean isApplicable(List<ImageConfiguration> configs) {
        return shouldAddImageConfiguration(configs)
                && MavenUtil.hasPlugin(getProject(), "io.thorntail:thorntail-maven-plugin")
                // if there's thorntail-kernel, it's Thorntail v4
                && !MavenUtil.hasDependency(getProject(), "io.thorntail", "thorntail-kernel");
    }

    @Override
    protected Map<String, String> getEnv(boolean isPrepackagePhase) throws MojoExecutionException {
        Map<String, String> ret = super.getEnv(isPrepackagePhase);
        // Switch off Prometheus agent until logging issue with WildFly Swarm is resolved
        // See:
        // - https://github.com/fabric8io/fabric8-maven-plugin/issues/1173
        // - https://issues.jboss.org/browse/THORN-1859
        ret.put("AB_PROMETHEUS_OFF", "true");
        return ret;
    }

}
