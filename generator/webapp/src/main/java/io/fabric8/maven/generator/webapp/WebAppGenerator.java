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
import io.fabric8.maven.generator.webapp.handler.CustomAppServerHandler;

import java.util.*;

/**
 * A generator for WAR apps
 *
 * @author kameshs
 */
public class WebAppGenerator extends BaseGenerator {

    private AppServerHandler appServerHandler;

    private enum Config implements Configs.Key {
        // Directory where to deploy to
        deploymentDir,

        // Unix user under which the war should be installed. If null, the default image user is used
        user,

        // Command to execute. If null, the base image default command is used
        cmd,

        // Ports to expose as a command separated list
        ports;

        protected String d;

        public String def() { return d; }
    }

    public WebAppGenerator(MavenGeneratorContext context) {
        super(context, "webapp");

        if (getFrom() != null) {
            // If a base image is provided use this exclusively and dont do a custom lookup
            appServerHandler = createCustomAppServerHandler(context);
        } else {
            appServerHandler = new AppServerDetector(context.getProject()).detect();
        }
    }

    private AppServerHandler createCustomAppServerHandler(MavenGeneratorContext context) {
        String from = getFrom();
        String user = getConfig(Config.user);
        String deploymentDir = getConfig(Config.deploymentDir,"/deployments");
        String command = getConfig(Config.cmd);
        List<String> ports = Arrays.asList(getConfig(Config.ports, "8080").split("\\s*,\\s*"));
        return new CustomAppServerHandler(from, deploymentDir, command, user, ports);
    }

    @Override
    public boolean isApplicable(List<ImageConfiguration> configs) {
        return shouldAddDefaultImage(configs) &&
               MavenUtil.hasPlugin(getProject(), "org.apache.maven.plugins:maven-war-plugin");
    }

    protected Map<String, String> getEnv() {
        Map<String, String> defaultEnv = new HashMap<>();
        defaultEnv.put("DEPLOY_DIR", getDeploymentDir());
        return defaultEnv;
    }

    @Override
    public List<ImageConfiguration> customize(List<ImageConfiguration> configs) {
        log.info("Using %s as base image for webapp",appServerHandler.getFrom());

        ImageConfiguration.Builder imageBuilder = new ImageConfiguration.Builder();

        BuildImageConfiguration.Builder buildBuilder = new BuildImageConfiguration.Builder()
                .assembly(createAssembly())
                .from(getFrom())
                .ports(appServerHandler.exposedPorts())
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
        AssemblyConfiguration.Builder builder = new AssemblyConfiguration.Builder()
                .basedir(getDeploymentDir())
                .descriptorRef("webapp");
        String user = getUser();
        if (user != null) {
            builder.user(user);
        }
        return builder.build();
    }

    @Override
    protected String getFrom() {
        String from = super.getFrom();
        return from != null ? from : appServerHandler.getFrom();
    }

    private String getDockerRunCommand() {
        String cmd = getConfig(Config.cmd);
        return cmd != null ? cmd : appServerHandler.getCommand();
    }

    private String getDeploymentDir() {
        String deploymentDir = getConfig(Config.deploymentDir);
        return deploymentDir != null ? deploymentDir : appServerHandler.getDeploymentDir();
    }

    private String getUser() {
        String user = getConfig(Config.user);
        return user != null ? user : appServerHandler.getUser();
    }
}
