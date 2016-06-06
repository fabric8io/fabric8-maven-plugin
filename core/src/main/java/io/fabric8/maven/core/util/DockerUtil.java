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

import org.apache.maven.project.MavenProject;

import static io.fabric8.maven.core.util.MavenProperties.DOCKER_IMAGE_LABEL;

/**
 */
public class DockerUtil {
    public static String prepareUserName(MavenProject project) {
        String groupId = project.getGroupId();
        int idx = groupId.lastIndexOf(".");
        String last = groupId.substring(idx != -1 ? idx : 0);
        StringBuilder ret = new StringBuilder();
        for (char c : last.toCharArray()) {
            if (Character.isLetter(c) || Character.isDigit(c)) {
                ret.append(c);
            }
        }
        return ret.toString();
    }

    public static String prepareImageNamePart(MavenProject project) {
        return project.getArtifactId().toLowerCase();
    }

    public static String getDockerLabel(MavenProject project) {
        return project.getProperties().getProperty(DOCKER_IMAGE_LABEL, project.getVersion());
    }
}
