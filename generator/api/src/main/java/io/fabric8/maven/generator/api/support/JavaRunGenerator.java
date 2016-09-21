package io.fabric8.maven.generator.api.support;
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

import java.util.*;

import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.generator.api.FromSelector;
import io.fabric8.maven.generator.api.MavenGeneratorContext;
import io.fabric8.utils.Strings;
import org.apache.maven.project.MavenProject;

/**
 * @author roland
 * @since 21/09/16
 */

abstract public class JavaRunGenerator extends BaseGenerator {

    public JavaRunGenerator(MavenGeneratorContext context, String name) {
        super(context, name, new FromSelector.Java(context));
    }

    private enum Config implements Configs.Key {
        webPort        {{ d = "8080"; }},
        jolokiaPort    {{ d = "8778"; }},
        prometheusPort {{ d = "9779"; }},
        baseDir        {{ d = "/deployments"; }},
        assemblyRef    {{ d = "artifact-with-includes"; }};

        public String def() { return d; } protected String d;
    }

    @Override
    public List<ImageConfiguration> customize(List<ImageConfiguration> configs) {
        if (isApplicable() && shouldAddDefaultImage(configs)) {
            ImageConfiguration.Builder imageBuilder = new ImageConfiguration.Builder();
            BuildImageConfiguration.Builder buildBuilder = new BuildImageConfiguration.Builder()
                .assembly(createAssembly())
                .from(getFrom())
                .ports(extractPorts());
            Map<String, String> envMap = getEnv();
            envMap.put("JAVA_APP_DIR", getConfig(Config.baseDir));
            buildBuilder.env(envMap);
            addLatestTagIfSnapshot(buildBuilder);
            imageBuilder
                .name(getImageName())
                .alias(getAlias())
                .buildConfig(buildBuilder.build());
            configs.add(imageBuilder.build());
            return configs;
        } else {
            return configs;
        }
    }

    /**
     * Hook for adding extra environment vars
     *
     * @param buildBuilder
     */
    protected Map<String, String> getEnv() {
        return new HashMap<>();
    }

    protected AssemblyConfiguration createAssembly() {
        return
            new AssemblyConfiguration.Builder()
                .basedir(getConfig(Config.baseDir))
                .descriptorRef(getAssemblyRef())
                .build();
    }

    protected String getAssemblyRef() {
        return getConfig(Config.assemblyRef);
    }

    protected List<String> extractPorts() {
        // TODO would rock to look at the base image and find the exposed ports!
        List<String> answer = new ArrayList<>();
        addPortIfValid(answer, getConfig(Config.webPort));
        addPortIfValid(answer, getConfig(Config.jolokiaPort));
        addPortIfValid(answer, getConfig(Config.prometheusPort));
        return answer;
    }

    private void addPortIfValid(List<String> list, String port) {
        if (Strings.isNotBlank(port) && Integer.parseInt(port) != 0) {
            list.add(port);
        }
    }
}
