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
package io.fabric8.maven.plugin.helm;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Represents the <a href="https://github.com/kubernetes/helm">Helm</a>
 * <a href="https://github.com/kubernetes/helm/blob/master/pkg/proto/hapi/chart/metadata.pb.go#L50">Chart.yaml file</a>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Chart {
    private String name;
    private String home;
    private List<String> sources;
    private String version;
    private String description;
    private List<String> keywords;
    private List<Maintainer> maintainers;
    private String engine;

    @Override
    public String toString() {
        return "Chart{" +
                "name='" + name + '\'' +
                ", version='" + version + '\'' +
                '}';
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHome() {
        return home;
    }

    public void setHome(String home) {
        this.home = home;
    }

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public List<Maintainer> getMaintainers() {
        return maintainers;
    }

    public void setMaintainers(List<Maintainer> maintainers) {
        this.maintainers = maintainers;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }
}
