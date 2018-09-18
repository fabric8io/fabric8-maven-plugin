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

import io.fabric8.maven.docker.util.Logger;

/**
 * @author roland
 * @since 23/07/16
 */
public class PrefixedLogger implements Logger {
    private final String prefix;
    private final Logger log;

    public PrefixedLogger(String prefix, Logger log) {
        this.prefix = prefix;
        this.log = log;
    }

    @Override
    public void debug(String message, Object... objects) {
        log.debug(p(message), objects);
    }

    @Override
    public void info(String message, Object... objects) {
        log.info(p(message),objects);
    }

    @Override
    public void verbose(String message, Object... objects) {
        log.verbose(p(message), objects);
    }

    @Override
    public void warn(String message, Object... objects) {
        log.warn(p(message), objects);
    }

    @Override
    public void error(String message, Object... objects) {
        log.error(p(message), objects);
    }

    @Override
    public String errorMessage(String message) {
        return log.errorMessage(p(message));
    }

    @Override
    public boolean isDebugEnabled() {
        return log.isDebugEnabled();
    }

    @Override
    public boolean isVerboseEnabled() {
        return log.isVerboseEnabled();
    }

    @Override
    public void progressStart() {
        log.progressStart();
    }

    @Override
    public void progressUpdate(String s, String s1, String s2) {
        log.progressUpdate(s,s1,s2);
    }

    @Override
    public void progressFinished() {
        log.progressFinished();
    }

    private String p(String message) {
        return prefix + ": " + message;
    }
}
