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
package io.fabric8.maven.plugin.mojo;

import java.io.File;
import org.apache.commons.lang3.StringUtils;

public class ResourceDirCreator {

    public static File getFinalResourceDir(File resourceDir, String environment) {
        if (resourceDir != null && StringUtils.isNotEmpty(environment)) {
            return new File(resourceDir, environment);
        }

        return resourceDir;
    }

}
