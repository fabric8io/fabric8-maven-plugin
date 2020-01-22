/**
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
package io.fabric8.maven.generator.api;

import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.RuntimeMode;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.regex.Pattern;

import static io.fabric8.maven.core.config.OpenShiftBuildStrategy.SourceStrategy.kind;
import static io.fabric8.maven.core.config.OpenShiftBuildStrategy.SourceStrategy.name;
import static io.fabric8.maven.core.config.OpenShiftBuildStrategy.SourceStrategy.namespace;

/**
 * Helper class to encapsulate the selection of a base image
 *
 * @author roland
 * @since 12/08/16
 */
public abstract class FromSelector {

    private final GeneratorContext context;

    private final Logger logger;

    private final Pattern REDHAT_VERSION_PATTERN = Pattern.compile("^.*\\.(redhat|fuse)-.*$");

    private Plugin plugin;
    private Plugin compilerPlugin;
    public FromSelector(GeneratorContext context, Logger logger) {
        this.context = context;
        this.logger = logger;
    }

    public String getFrom() {
        RuntimeMode mode = context.getRuntimeMode();
        OpenShiftBuildStrategy strategy = context.getStrategy();
        if (mode == RuntimeMode.openshift && strategy == OpenShiftBuildStrategy.s2i) {
            return getS2iBuildFrom();
        } else {
            return getDockerBuildFrom();
        }
    }

    public Map<String, String> getImageStreamTagFromExt() {
        Map<String, String> ret = new HashMap<>();
        ret.put(kind.key(), "ImageStreamTag");
        ret.put(namespace.key(), "openshift");
        ret.put(name.key(), getIstagFrom());
        return ret;
    }

    abstract protected String getDockerBuildFrom();
    abstract protected String getS2iBuildFrom();
    abstract protected String getIstagFrom();

    public boolean isRedHat() {
        MavenProject project = context.getProject();

        plugin = MavenUtil.getPlugin(project, "io.fabric8", "fabric8-maven-plugin");
        if (plugin == null) {
            // This plugin might be repackaged.
            plugin = project.getPlugin("org.jboss.redhat-fuse:fabric8-maven-plugin");
        }
        if (plugin == null) {
            // Can happen if not configured in a build section but only in a dependency management section
            return false;
        }
        String version = plugin.getVersion();
        return REDHAT_VERSION_PATTERN.matcher(version).matches();
    }

    public boolean isJava11() {
        MavenProject project = context.getProject();
        compilerPlugin = MavenUtil.getPlugin(project, "org.apache.maven.plugins", "maven-compiler-plugin");

        if (compilerPlugin == null) {
            return false;
        }

        Xpp3Dom dom = (Xpp3Dom)compilerPlugin.getConfiguration();

        String releaseVersion = "8";
        if (dom != null && dom.getChild("release") != null) {
            releaseVersion = dom.getChild("release").getValue();
        } else if (dom != null && dom.getChild("target") != null) {
            releaseVersion = dom.getChild("target").getValue();
        }

        Properties properties = project.getProperties();

        if (isEleven(releaseVersion)) {
            return true;
        }

        try {
            releaseVersion = (String) properties.entrySet()
                    .stream().filter(entry -> entry.getKey().equals("maven.compiler.release")
                            || entry.getKey().equals("maven.compiler.target")).findFirst()
                    .get().getValue();
        } catch (NoSuchElementException e) {
            return false;
        }

        if (isEleven(releaseVersion)) {
            return true;
        }

        return false;
    }

    private boolean isEleven(String s) {
        boolean result = false;
        try {
            result = Configs.asInt(s) == 11;
        } catch (NumberFormatException e) {
            // The user has set a 1.x (eg, 1.8) value as target/release java version.
            logger.debug("%.1f target Java version detected", Configs.asFloat(s));
        } finally {
            return result;
        }
    }

    public static class Default extends FromSelector {

        private final String upstreamDocker;
        private final String upstreamS2i;
        private final String redhatDocker;
        private final String redhatS2i;
        private final String redhatIstag;
        private final String upstreamIstag;

        public Default(GeneratorContext context, String prefix, Logger logger) {
            super(context, logger);
            DefaultImageLookup lookup = new DefaultImageLookup(Default.class);

            this.upstreamDocker = prefix.equals("java") || prefix.equals("test")?
                    isJava11()? lookup.getImageName(prefix + ".11.upstream.docker"):
                    lookup.getImageName(prefix + ".8.upstream.docker"):
                    lookup.getImageName(prefix + ".upstream.docker");
            this.upstreamS2i = lookup.getImageName(prefix + ".upstream.s2i");
            this.upstreamIstag = lookup.getImageName(prefix + ".upstream.istag");

            this.redhatDocker = lookup.getImageName(prefix + ".redhat.docker");
            this.redhatS2i = lookup.getImageName(prefix + ".redhat.s2i");
            this.redhatIstag = lookup.getImageName(prefix + ".redhat.istag");
        }

        @Override
        protected String getDockerBuildFrom() {
            return isRedHat() ? redhatDocker : upstreamDocker;
        }

        @Override
        protected String getS2iBuildFrom() {
            return isRedHat() ? redhatS2i : upstreamS2i;
        }

        protected String getIstagFrom() {
            return isRedHat() ? redhatIstag : upstreamIstag;
        }
    }
}
