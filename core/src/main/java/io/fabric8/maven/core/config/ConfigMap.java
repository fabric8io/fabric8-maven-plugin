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
package io.fabric8.maven.core.config;

import java.util.ArrayList;
import java.util.List;

public class ConfigMap {

    private String name;
    private List<ConfigMapEntry> entries = new ArrayList<>();

    public void addEntry(ConfigMapEntry configMapEntry) {
        this.entries.add(configMapEntry);
    }

    public List<ConfigMapEntry> getEntries() {
        return entries;
    }

    /**
     * Set the name of ConfigMap.
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Return the name of ConfigMap.
     * @return
     */
    public String getName() {
        return name;
    }
}
