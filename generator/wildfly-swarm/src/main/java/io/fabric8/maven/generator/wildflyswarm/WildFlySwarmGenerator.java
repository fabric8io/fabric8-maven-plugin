/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.maven.generator.wildflyswarm;

import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.generator.api.MavenGeneratorContext;
import io.fabric8.maven.generator.api.support.JavaRunGenerator;
import org.apache.maven.project.MavenProject;

import java.util.List;
import java.util.Map;

/**
 * Created by ceposta
 * <a href="http://christianposta.com/blog>http://christianposta.com/blog</a>.
 */
public class WildFlySwarmGenerator extends JavaRunGenerator {

    public WildFlySwarmGenerator(MavenGeneratorContext context) {
        super(context, "wildfly-swarm");
    }

    private enum Config implements Configs.Key {
        assemblyRef    {{ d = "wildfly-swarm"; }};

        public String def() { return d; } protected String d;
    }

    @Override
    protected String getAssemblyRef() {
        return getConfig(Config.assemblyRef);
    }

    @Override
    protected Map<String, String> getEnv() {
        Map<String, String> ret = super.getEnv();
        // Switch off agent_bond until logging issue with wilfdlfy-swarm is resolved
        // See:
        // - https://github.com/fabric8io/fabric8-maven-plugin/issues/320
        // - https://github.com/rhuss/jolokia/pull/260
        // - https://issues.jboss.org/browse/SWARM-204
        ret.put("AB_OFF", "true");
        ret.put("AB_JOLOKIA_OFF", "true");
        return ret;
    }

    @Override
    public boolean isApplicable(List<ImageConfiguration> configs) {
        return shouldAddDefaultImage(configs) &&
               MavenUtil.hasPlugin(getProject(), "org.wildfly.swarm:wildfly-swarm-plugin");
    }

}
