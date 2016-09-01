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
import io.fabric8.maven.generator.api.BaseGenerator;
import io.fabric8.maven.generator.api.FromSelector;
import io.fabric8.maven.generator.api.MavenGeneratorContext;
import io.fabric8.utils.Strings;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ceposta 
 * <a href="http://christianposta.com/blog>http://christianposta.com/blog</a>.
 */
public class WildFlySwarmGenerator extends BaseGenerator {


    public WildFlySwarmGenerator(MavenGeneratorContext context) {
        super(context, "wildfly-swarm", new FromSelector.Default(context,
                "fabric8/java-alpine-openjdk8-jdk", "fabric8/s2i-java",
                "jboss-fuse-6/fis-java-openshift", "jboss-fuse-6/fis-java-openshift"));
    }

    private enum Config implements Configs.Key {
        webPort        {{ d = "8080"; }},
        jolokiaPort    {{ d = "8778"; }},
        prometheusPort {{ d = "9779"; }};

        public String def() { return d; } protected String d;
    }

    @Override
    public boolean isApplicable() {
        MavenProject project = getProject();
        return MavenUtil.hasPlugin(project, "org.wildfly.swarm:wildfly-swarm-plugin");
    }

    @Override
    public List<ImageConfiguration> customize(List<ImageConfiguration> configs) {
        if (isApplicable() && shouldAddDefaultImage(configs)) {
            ImageConfiguration.Builder imageBuilder = new ImageConfiguration.Builder();
            BuildImageConfiguration.Builder buildBuilder = new BuildImageConfiguration.Builder()
                    .assembly(createAssembly())
                    .from(getFrom())
                    .ports(extractPorts());
            addLatestTagIfSnapshot(buildBuilder);
            imageBuilder
                    .name(getImageName())
                    .alias(getAlias())
                    .buildConfig(buildBuilder.build());
            configs.add(imageBuilder.build());
            return configs;
        }else {
            return configs;
        }
    }

    private List<String> extractPorts() {
        // TODO would rock to look at the base image and find the exposed ports!
        List<String> answer = new ArrayList<>();
        addPortIfValid(answer, getConfig(Config.webPort));
        addPortIfValid(answer, getConfig(Config.jolokiaPort));
        addPortIfValid(answer, getConfig(Config.prometheusPort));
        return answer;
    }

    private void addPortIfValid(List<String> list, String port) {
        if (Strings.isNotBlank(port)) {
            list.add(port);
        }
    }


    private AssemblyConfiguration createAssembly() {
        return
                new AssemblyConfiguration.Builder()
                        .basedir("/app")
                        .descriptorRef("wildfly-swarm")
                        .build();
    }
}
