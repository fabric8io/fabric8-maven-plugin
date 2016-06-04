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

/**
 * This class contains all of the special maven properties we use in the plugin so we can automatically generate
 * documentation for them
 */
public class MavenProperties {

    /**
     * The default user used for docker images which is based on the <code>project.groupId</code>
     */
    public static final String DOCKER_IMAGE_USER = "fabric8.docker.user";

    /**
     * The default user used for docker images which is based on the <code>project.artifactId</code>
     */
    public static final String DOCKER_IMAGE_NAME = "fabric8.docker.name";

    /**
     * The default docker image label. If not using a SNAPSHOT <code>project.version</code> then this
     * value is the <code>project.version</code> otherwise its <code>latest</code>
     */
    public static final String DOCKER_IMAGE_LABEL = "fabric8.docker.label";


    public static final String[] MAVEN_PROPERTIES = {
            DOCKER_IMAGE_USER, DOCKER_IMAGE_NAME, DOCKER_IMAGE_LABEL
    };
}
