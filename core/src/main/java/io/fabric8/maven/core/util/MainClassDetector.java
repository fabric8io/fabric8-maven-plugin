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
package io.fabric8.maven.core.util;

import java.io.File;
import java.io.IOException;
import java.util.List;

import io.fabric8.maven.core.util.ClassUtil;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * @author roland
 * @since 11/11/16
 */
public class MainClassDetector {

    private String mainClass = null;
    private final File classesDir;
    private final Logger log;

    public MainClassDetector(String mainClass, File classesDir, Logger log) {
        this.mainClass = mainClass;
        this.classesDir = classesDir;
        this.log = log;
    }

    public String getMainClass() throws MojoExecutionException {
        if (mainClass != null) {
            return mainClass;
        }

        // Try to detect a single main class from target/classes
        try {
            List<String> foundMainClasses = ClassUtil.findMainClasses(classesDir);
            if (foundMainClasses.size() == 0) {
                return mainClass = null;
            } else if (foundMainClasses.size() == 1) {
                return mainClass = foundMainClasses.get(0);
            } else {
                log.warn("Found more than one main class : %s. Ignoring ....",  foundMainClasses);
                return mainClass = null;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Can not examine main classes: " + e, e);
        }
    }
}
