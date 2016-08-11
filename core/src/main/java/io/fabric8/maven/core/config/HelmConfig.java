package io.fabric8.maven.core.config;
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

import java.util.List;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * Configuration for a helm chart
 * @author roland
 * @since 11/08/16
 */
public class HelmConfig {

    @Parameter
    private String chart;

    @Parameter
    private String outputDir;

    @Parameter
    private String sourceDir;

    @Parameter
    private List<String> keywords;

    @Parameter
    private String engine;

    @Parameter
    private List<HelmType> type;

    public String getChart() {
        return chart;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public String getSourceDir() {
        return sourceDir;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public String getEngine() {
        return engine;
    }

    public List<HelmType> getType() {
        return type;
    }

    public enum HelmType {
        kubernetes("helm", "kubernetes", "Kubernetes"),
        openshift("helmshift", "openshift", "OpenShift");

        private final String classifier;
        private final String sourceDir;
        private final String description;

        HelmType(String classifier, String sourceDir, String description) {
            this.classifier = classifier;
            this.sourceDir = sourceDir;
            this.description = description;
        }

        public String getClassifier() {
            return classifier;
        }

        public String getSourceDir() {
            return sourceDir;
        }

        public String getDescription() {
            return description;
        }
    }
}
