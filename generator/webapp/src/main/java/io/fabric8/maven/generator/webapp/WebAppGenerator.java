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
package io.fabric8.maven.generator.webapp;

import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.generator.api.MavenGeneratorContext;
import io.fabric8.maven.generator.api.support.BaseGenerator;
import io.fabric8.utils.Strings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author kameshs
 */
public class WebAppGenerator extends BaseGenerator {


    private AppServerDetector appServerDetector;

    public WebAppGenerator(MavenGeneratorContext context) {
        super(context, "webapp");

        appServerDetector = AppServerDetectorFactory
                .getInstance(getProject())
                .whichAppKindOfAppServer();


    }

    @Override
    public boolean isApplicable(List<ImageConfiguration> configs) {
        return shouldAddDefaultImage(configs) &&
                MavenUtil.hasPlugin(getProject(), "org.apache.maven.plugins:maven-war-plugin");
    }

    protected Map<String, String> getEnv() {
        Map<String, String> defaultEnv = new HashMap<>();
        defaultEnv.put("DEPLOY_DIR", appServerDetector.getDeploymentDir());
        return defaultEnv;
    }

    @Override
    public List<ImageConfiguration> customize(List<ImageConfiguration> configs) {
        ImageConfiguration.Builder imageBuilder = new ImageConfiguration.Builder();

        BuildImageConfiguration.Builder buildBuilder = new BuildImageConfiguration.Builder()
                .assembly(createAssembly())
                .from(getFrom())
                .ports(appServerDetector.exposedPorts())
                .cmd(getDockerRunCommand())
                .env(getEnv());
        addLatestTagIfSnapshot(buildBuilder);
        imageBuilder
                .name(getImageName())
                .alias(getAlias())
                .buildConfig(buildBuilder.build());
        configs.add(imageBuilder.build());

        return configs;
    }

    private AssemblyConfiguration createAssembly() {
        return new AssemblyConfiguration.Builder()
                .basedir(getDeploymentDir())
                .user(getConfig(Config.user))
                .descriptorRef("webapp")
                .build();
    }

    @Override
    protected String getFrom() {

        String from = super.getFrom();

        if (from == null) {
            return appServerDetector.getFrom();

        }
        return from;
    }

    private String getDockerRunCommand() {

        String cmd = getConfig(Config.cmd, null);

        if (cmd == null) {
            return appServerDetector.getCommand();
        }

        return cmd;
    }

    private String getDeploymentDir() {

        String defaultBaseDir = getConfig(Config.deploymentDir, null);

        if (defaultBaseDir == null) {
            return appServerDetector.getDeploymentDir();
        } else {
            return defaultBaseDir;
        }
    }

    private enum Config implements Configs.Key {
        deploymentDir {{d = "/deployments";}},
        user {{ d = "jboss:jboss:jboss"; }},
        cmd {{ d = "/opt/tomcat/bin/deploy-and-run.sh"; }};

        protected String d;

        public String def() { return d; }
    }
}
