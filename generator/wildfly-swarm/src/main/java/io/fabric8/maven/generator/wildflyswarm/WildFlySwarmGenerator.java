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
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.generator.api.support.BaseGenerator;
import io.fabric8.maven.generator.api.FromSelector;
import io.fabric8.maven.generator.api.MavenGeneratorContext;
import io.fabric8.maven.generator.api.support.JavaRunGenerator;
import io.fabric8.utils.Strings;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;

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
    public boolean isApplicable() {
        MavenProject project = getProject();
        return MavenUtil.hasPlugin(project, "org.wildfly.swarm:wildfly-swarm-plugin");
    }

}
