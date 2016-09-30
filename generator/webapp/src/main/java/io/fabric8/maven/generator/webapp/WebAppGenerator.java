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

    private static final String DOCKER_TOMCAT_FROM = "fabric8/tomcat-8";
    private static final String DOCKER_JETTY_FROM = "fabric8/jetty-9";
    private static final String DOCKER_WILDFLY_FROM = "jboss/wildfly";

    //TODO need to update the s2i images
    private static final String VANNILA_S2I_FROM = "fabric8/tomcat-8";
    private static final String REDHAT_DOCKER_S2I = "jboss-eap-6/eap64-openshift";

    public WebAppGenerator(MavenGeneratorContext context) {
        super(context, "webapp");
    }

    private enum Config implements Configs.Key {
        deploymentDir  {{ d = "/deployments"; }},
        user           {{ d = "jboss:jboss:jboss"; }},
        cmd            {{ d = "/opt/tomcat/bin/deploy-and-run.sh"; }},
        webPort        {{ d = "8080"; }},
        jolokiaPort    {{ d = "8778"; }};

        public String def() { return d; } protected String d;
    }


    @Override
    public boolean isApplicable(List<ImageConfiguration> configs) {
        return shouldAddDefaultImage(configs) &&
                MavenUtil.hasPlugin(getProject(), "org.apache.maven.plugins:maven-war-plugin");
    }

    protected Map<String, String> getEnv() {
        Map<String, String> defaultEnv = new HashMap<>();
        defaultEnv.put("DEPLOY_DIR", getConfig(Config.deploymentDir));
        return defaultEnv;
    }

    @Override
    public List<ImageConfiguration> customize(List<ImageConfiguration> configs) {
        ImageConfiguration.Builder imageBuilder = new ImageConfiguration.Builder();

        BuildImageConfiguration.Builder buildBuilder = new BuildImageConfiguration.Builder()
                .assembly(createAssembly())
                .from(getFrom())
                .ports(extractPorts())
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
                .basedir(getDefaultBaseDir())
                .user(getConfig(Config.user))
                .descriptorRef("webapp")
                .build();
    }


    @Override
    protected String getFrom() {
        String from = super.getFrom();

        if (from == null) {

            if (isJetty()) {
                return DOCKER_JETTY_FROM;
            } else if (isTomcat()) {
                return DOCKER_TOMCAT_FROM;
            } else if (isWildFly()) {
                return DOCKER_WILDFLY_FROM;
            } else {
                return DOCKER_TOMCAT_FROM;
            }

        }
        return from;
    }

    private String getDockerRunCommand(){

        String cmd = getConfig(Config.cmd);
        //since we use jboss/wildfly image the fabric8 deploy-and-run.sh will not work
        //we also make sure the user has not added custom cmd command to maven config
        if("/opt/tomcat/bin/deploy-and-run.sh".equals(cmd)) {
            if (isWildFly()) {
                cmd = "/opt/jboss/wildfly/bin/standalone.sh";
            } else if (isJetty()) {
                cmd = "/opt/jetty/bin/deploy-and-run.sh";
            }
        }

        return cmd;
    }

    private String getDefaultBaseDir() {

        String defaultBaseDir = getConfig(Config.deploymentDir);

        if(isWildFly()
                && "/deployments".equals(defaultBaseDir)){
            defaultBaseDir = "/opt/jboss/wildfly/standalone/deployments";
        }
        return defaultBaseDir;
    }

    private boolean isJetty(){

        return hasJettyPlugin() ||
                AppSeverDetector.hasJettyFiles(getProject());
    }

    private boolean isTomcat(){

        return AppSeverDetector.hasTomcatFiles(getProject());
    }

    private boolean isWildFly(){

        return hasJBossOrWildFlyPlugin()||
                AppSeverDetector.hasWildFlyFiles(getProject());
    }

    private boolean hasJBossOrWildFlyPlugin(){
       return
               MavenUtil.hasPlugin(getProject(), "org.jboss.as.plugins:jboss-as-maven-plugin")
               || MavenUtil.hasPlugin(getProject(), "org.wildfly.plugins:wildfly-maven-plugin");
    }

    private boolean hasJettyPlugin(){
        return
                MavenUtil.hasPlugin(getProject(), "org.mortbay.jetty:jetty-maven-plugin")
                        || MavenUtil.hasPlugin(getProject(), "org.eclipse.jetty:jetty-maven-plugin");
    }
}
