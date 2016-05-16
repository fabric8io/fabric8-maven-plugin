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

package io.fabric8.maven.customizer.api;

import java.util.List;

import io.fabric8.maven.docker.config.ImageConfiguration;

/**
 * @author roland
 * @since 15/05/16
 */
public interface Customizer {
      /**
     * Provide additional image configurations
     *
     * @param existingConfigs the already detected and resolved configuration
     * @return list of image configurations
     */
    List<ImageConfiguration> customize(List<ImageConfiguration> existingConfigs);
}





