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

import java.util.Objects;

public class ConfigMapEntry {

    private String name;
    private String value;
    private String file;

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public String getFile() {
        return file;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setFile(String file) {
        this.file = file;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ConfigMapEntry that = (ConfigMapEntry) o;
        return Objects.equals(name, that.name) &&
            Objects.equals(value, that.value) &&
            Objects.equals(file, that.file);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value, file);
    }
}
