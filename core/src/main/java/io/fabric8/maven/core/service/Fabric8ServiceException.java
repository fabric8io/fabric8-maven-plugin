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
package io.fabric8.maven.core.service;

/**
 * @author nicola
 * @since 17/02/2017
 */
public class Fabric8ServiceException extends Exception {

    public Fabric8ServiceException() {
    }

    public Fabric8ServiceException(String message) {
        super(message);
    }

    public Fabric8ServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public Fabric8ServiceException(Throwable cause) {
        super(cause);
    }

    public Fabric8ServiceException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
