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
package io.fabric8.maven.generator.karaf;

import java.util.ArrayList;
import java.util.List;

import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.generator.api.support.BaseGenerator;
import io.fabric8.maven.generator.api.FromSelector;
import io.fabric8.maven.generator.api.MavenGeneratorContext;
import io.fabric8.utils.Strings;
import org.apache.maven.project.MavenProject;

public class KarafGenerator extends BaseGenerator {

    private static final String IMAGE_S2I_KARAF_VERSION = "1.3.3";

    public KarafGenerator(MavenGeneratorContext context) {
        super(context, "karaf", new FromSelector.Default(context,
            "fabric8/s2i-karaf:" + IMAGE_S2I_KARAF_VERSION, "fabric8/s2i-karaf:" + IMAGE_S2I_KARAF_VERSION,
            "jboss-fuse-6/fis-karaf-openshift", "jboss-fuse-6/fis-karaf-openshift"));
    }

    private enum Config implements Configs.Key {
        baseDir        {{ d = "/deployments/"; }},
        user           {{ d = "jboss:jboss:jboss"; }},
        cmd            {{ d = "/deployments/deploy-and-run.sh"; }},
        webPort        {{ d = "8181"; }},
        jolokiaPort    {{ d = "8778"; }};

        public String def() { return d; } protected String d;
    }

    @Override
    public List<ImageConfiguration> customize(List<ImageConfiguration> configs) {
        ImageConfiguration.Builder imageBuilder = new ImageConfiguration.Builder();
        BuildImageConfiguration.Builder buildBuilder = new BuildImageConfiguration.Builder()
            .assembly(createAssembly())
                .from(getFrom())
                .ports(extractPorts())
                .cmd(getConfig(Config.cmd));
            addLatestTagIfSnapshot(buildBuilder);
            imageBuilder
                .name(getImageName())
                .alias(getAlias())
                .buildConfig(buildBuilder.build());
            configs.add(imageBuilder.build());
        return configs;
    }

    @Override
    public boolean isApplicable(List<ImageConfiguration> configs) {
        return shouldAddDefaultImage(configs) &&
               MavenUtil.hasPlugin(getProject(), "org.apache.karaf.tooling:karaf-maven-plugin");
    }

    protected List<String> extractPorts() {
        List<String> answer = new ArrayList<>();
        addPortIfValid(answer, getConfig(Config.webPort));
        addPortIfValid(answer, getConfig(Config.jolokiaPort));
        return answer;
    }

    private void addPortIfValid(List<String> list, String port) {
        if (Strings.isNotBlank(port)) {
            list.add(port);
        }
    }

    private AssemblyConfiguration createAssembly() {
        return new AssemblyConfiguration.Builder()
            .basedir(getConfig(Config.baseDir))
            .user(getConfig(Config.user))
            .descriptorRef("karaf")
            .build();
    }
}
