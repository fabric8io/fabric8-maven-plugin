/*
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
package io.fabric8.maven.core.util;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.HasMetadata;

/**
 * Represents a key for a resource so we can look up resources by key and name
 */
public class KindAndName {
    private final String kind;
    private final String name;

    public KindAndName(String kind, String name) {
        this.kind = kind;
        this.name = name;
    }

    public KindAndName(HasMetadata item) {
        this(KubernetesHelper.getKind(item), KubernetesHelper.getName(item));
    }

    public String getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "KindAndName{" +
                "kind='" + kind + '\'' +
                ", name='" + name + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        KindAndName that = (KindAndName) o;

        if (!kind.equals(that.kind))
            return false;

        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        int result = kind.hashCode();
        result = 31 * result + name.hashCode();
        return result;
    }
}
