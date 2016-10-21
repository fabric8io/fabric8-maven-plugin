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

import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.PlatformMode;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.handler.property.ConfigKey;
import io.fabric8.maven.generator.api.MavenGeneratorContext;
import io.fabric8.maven.generator.api.support.BaseGenerator;
import io.fabric8.maven.generator.webapp.handler.CustomAppServerHandler;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * A generator for WAR apps
 *
 * @author kameshs
 */
public class WebAppGenerator extends BaseGenerator {

    private enum Config implements Configs.Key {
        // App server to use (like 'tomcat', 'jetty', 'wildfly'
        server,

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
    }

    @Override
    public boolean isApplicable(List<ImageConfiguration> configs) {
        return shouldAddDefaultImage(configs) &&
               MavenUtil.hasPlugin(getProject(), "org.apache.maven.plugins:maven-war-plugin");
    }

    @Override
    public List<ImageConfiguration> customize(List<ImageConfiguration> configs) {
        if (getContext().getMode() == PlatformMode.openshift &&
            getContext().getStrategy() == OpenShiftBuildStrategy.s2i) {
            throw new IllegalArgumentException("S2I not yet supported for the webapp-generator. Use -Dfabric8.mode=kubernetes or " +
                                               "-Dfabric8.buildStrategy=docker for OpenShift mode. Please refer to the reference manual at " +
                                               "https://maven.fabric8.io for details about build modes.");
        }

        // Late initialization to avoid unnecessary directory scanning
        AppServerHandler handler = getAppServerHandler(getContext());

        log.info("Using %s as base image for webapp",handler.getFrom());

        ImageConfiguration.Builder imageBuilder = new ImageConfiguration.Builder();

        BuildImageConfiguration.Builder buildBuilder = new BuildImageConfiguration.Builder()
            .assembly(createAssembly(handler))
            .from(getFrom(handler))
            .ports(handler.exposedPorts())
            .cmd(getDockerRunCommand(handler))
            .env(getEnv(handler));
        addLatestTagIfSnapshot(buildBuilder);
        imageBuilder
            .name(getImageName())
            .alias(getAlias())
            .buildConfig(buildBuilder.build());
        configs.add(imageBuilder.build());

        return configs;
    }

    private AppServerHandler getAppServerHandler(MavenGeneratorContext context) {
        String from = super.getFrom();
        if (from != null) {
            // If a base image is provided use this exclusively and dont do a custom lookup
            return createCustomAppServerHandler(from);
        } else {
            return new AppServerDetector(context.getProject()).detect(getConfig(Config.server));
        }
    }

    private AppServerHandler createCustomAppServerHandler(String from) {
        String user = getConfig(Config.user);
        String deploymentDir = getConfig(Config.deploymentDir,"/deployments");
        String command = getConfig(Config.cmd);
        List<String> ports = Arrays.asList(getConfig(Config.ports, "8080").split("\\s*,\\s*"));
        return new CustomAppServerHandler(from, deploymentDir, command, user, ports);
    }

    protected Map<String, String> getEnv(AppServerHandler handler) {
        Map<String, String> defaultEnv = new HashMap<>();
        defaultEnv.put("DEPLOY_DIR", getDeploymentDir(handler));
        return defaultEnv;
    }

    private AssemblyConfiguration createAssembly(AppServerHandler handler) {
        AssemblyConfiguration.Builder builder = new AssemblyConfiguration.Builder()
                .basedir(getDeploymentDir(handler))
                .descriptorRef("webapp");
        String user = getUser(handler);
        if (user != null) {
            builder.user(user);
        }
        return builder.build();
    }

    // To be called **only** from customize() as they require an already
    // initialized appServerHandler:
    protected String getFrom(AppServerHandler handler) {
        String from = super.getFrom();
        return from != null ? from : handler.getFrom();
    }

    private String getDockerRunCommand(AppServerHandler handler) {
        String cmd = getConfig(Config.cmd);
        return cmd != null ? cmd : handler.getCommand();
    }

    private String getDeploymentDir(AppServerHandler handler) {
        String deploymentDir = getConfig(Config.deploymentDir);
        return deploymentDir != null ? deploymentDir : handler.getDeploymentDir();
    }

    private String getUser(AppServerHandler handler) {
        String user = getConfig(Config.user);
        return user != null ? user : handler.getUser();
    }
}
