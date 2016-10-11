package io.fabric8.maven.generator.api;
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

import java.util.regex.Pattern;

import io.fabric8.maven.core.config.OpenShiftBuildStrategy;
import io.fabric8.maven.core.config.PlatformMode;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;

/**
 * Helper class to encapsulate the selection of a base image
 *
 * @author roland
 * @since 12/08/16
 */
public abstract class FromSelector {

    private final MavenGeneratorContext context;

    private final Pattern REDHAT_VERSION_PATTERN = Pattern.compile("^.*\\.(redhat|fuse)-.*$");

    public FromSelector(MavenGeneratorContext context) {
        this.context = context;
    }

    public String getFrom() {
        PlatformMode mode = context.getMode();
        OpenShiftBuildStrategy strategy = context.getStrategy();
        if (mode == PlatformMode.openshift && strategy == OpenShiftBuildStrategy.s2i) {
            return getS2iBuildFrom();
        } else {
            return getDockerBuildFrom();
        }
    }

    abstract protected String getDockerBuildFrom();
    abstract protected String getS2iBuildFrom();

    public boolean isRedHat() {
        MavenProject project = context.getProject();
        Plugin plugin = project.getPlugin("io.fabric8:fabric8-maven-plugin");
        if (plugin == null) {
            // Can happen if not configured in a build section but only in a dependency management section
            return false;
        }
        String version = plugin.getVersion();
        return REDHAT_VERSION_PATTERN.matcher(version).matches();
    }

    public static class Default extends FromSelector {

        private final String upstreamDocker;
        private final String upstreamS2i;
        private final String redhatDocker;
        private final String redhatS2i;

        public Default(MavenGeneratorContext context, String prefix) {
            super(context);
            DefaultImageLookup lookup = new DefaultImageLookup(Default.class);
            this.upstreamDocker = lookup.getImageName(prefix + ".upstream.docker");
            this.upstreamS2i = lookup.getImageName(prefix + ".upstream.s2i");
            this.redhatDocker = lookup.getImageName(prefix + ".redhat.docker");
            this.redhatS2i = lookup.getImageName(prefix + ".redhat.s2i");
        }

        @Override
        protected String getDockerBuildFrom() {
            return isRedHat() ? redhatDocker : upstreamDocker;
        }

        @Override
        protected String getS2iBuildFrom() {
            return isRedHat() ? redhatS2i : upstreamS2i;
        }
    }
}
