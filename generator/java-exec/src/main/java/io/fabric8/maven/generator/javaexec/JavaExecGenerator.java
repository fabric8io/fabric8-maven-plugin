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

package io.fabric8.maven.generator.javaexec;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.maven.core.util.ClassUtil;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.generator.api.BaseGenerator;
import io.fabric8.maven.generator.api.FromSelector;
import io.fabric8.maven.generator.api.MavenGeneratorContext;
import io.fabric8.utils.Strings;

/**
 */
public class JavaExecGenerator extends BaseGenerator {

    public static final String JAVA_MAIN_CLASS = "JAVA_MAIN_CLASS";

    public JavaExecGenerator(MavenGeneratorContext context) {
        super(context, "java-exec", new FromSelector.Java(context));
    }

    private enum Config implements Configs.Key {
        // The name of the main class. If not speficied it is tried
        // to find a main class within target/classes
        mainClass,
        webPort,
        jolokiaPort    {{ d = "8778"; }},
        prometheusPort {{ d = "9779"; }};

        public String def() { return d; } protected String d;
    }

    @Override
    public List<ImageConfiguration> customize(List<ImageConfiguration> configs) {
        if (isApplicable() && shouldAddDefaultImage(configs)) {
            Map<String, String> envVars = new HashMap<>();
            envVars.put(JAVA_MAIN_CLASS, getMainClass());

            ImageConfiguration.Builder imageBuilder = new ImageConfiguration.Builder();
            BuildImageConfiguration.Builder buildBuilder = new BuildImageConfiguration.Builder()
                .assembly(createAssembly())
                .from(getFrom())
                .ports(extractPorts())
                .env(envVars);
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

    // Only extract one time
    private String mainClass = null;
    private boolean alreadySearchedForMainClass = false;

    private String getMainClass() {
        if (this.alreadySearchedForMainClass) {
            return this.mainClass;
        }

        String mc = getConfig(Config.mainClass);
        if (mc != null) {
            return mc;
        }

        // Try to detect a single main class from target/classes
        try {
            List<String> foundMainClasses =
                ClassUtil.findMainClasses(new File(getContext().getProject().getBuild().getOutputDirectory()));
            if (foundMainClasses.size() == 0) {
                return mainClass = null;
            } else if (foundMainClasses.size() == 1) {
                return mainClass = foundMainClasses.get(0);
            } else {
                log.warn("Found more than one main class : " + foundMainClasses + ". Ignoring ....");
                return mainClass = null;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot examine main classes: " + e,e);
        } finally {
            alreadySearchedForMainClass = true;
        }
    }

    @Override
    public boolean isApplicable() {
        return Strings.isNotBlank(getMainClass());
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
                .descriptorRef("java-app")
                .build();
    }
}
