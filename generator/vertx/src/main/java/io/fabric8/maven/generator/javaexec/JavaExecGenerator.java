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

package io.fabric8.maven.generator.javaexec;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.maven.core.util.ClassUtil;
import io.fabric8.maven.core.util.Configs;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.generator.api.MavenGeneratorContext;
import io.fabric8.maven.generator.api.support.JavaRunGenerator;
import io.fabric8.utils.Strings;

/**
 */
public class JavaExecGenerator extends JavaRunGenerator {

    public static final String JAVA_MAIN_CLASS = "JAVA_MAIN_CLASS";

    public JavaExecGenerator(MavenGeneratorContext context) {
        super(context, "java-exec");
    }

    private enum Config implements Configs.Key {
        // The name of the main class. If not speficied it is tried
        // to find a main class within target/classes
        mainClass;

        public String def() { return d; } protected String d;
    }


    @Override
    protected Map<String, String> getEnv() {
        Map<String, String> ret = super.getEnv();
        ret.put(JAVA_MAIN_CLASS, getMainClass());
        return ret;
    }

    // Only extract one time
    private String mainClass = null;
    private boolean alreadySearchedForMainClass = false;

    private String getMainClass() {
        if (this.alreadySearchedForMainClass) {
            return this.mainClass;
        }

        String mc = getConfig(Config.mainClass);
        if (mc != null) {
            return mc;
        }

        // Try to detect a single main class from target/classes
        try {
            List<String> foundMainClasses =
                ClassUtil.findMainClasses(new File(getContext().getProject().getBuild().getOutputDirectory()));
            if (foundMainClasses.size() == 0) {
                return mainClass = null;
            } else if (foundMainClasses.size() == 1) {
                return mainClass = foundMainClasses.get(0);
            } else {
                log.warn("Found more than one main class : " + foundMainClasses + ". Ignoring ....");
                return mainClass = null;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Can not examine main classes: " + e,e);
        } finally {
            alreadySearchedForMainClass = true;
        }
    }

    @Override
    public boolean isApplicable(List<ImageConfiguration> configs) {
        return shouldAddDefaultImage(configs) && Strings.isNotBlank(getMainClass());
    }
}
