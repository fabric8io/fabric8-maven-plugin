package io.fabric8.maven.plugin.config;
/*
 * 
 * Copyright 2016 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author roland
 * @since 22/03/16
 */
public class AnnotationConfiguration {

    @Parameter
    private Map<String, String> pod;

    @Parameter
    private Map<String, String> rc;

    @Parameter
    private Map<String, String> service;

    @Parameter
    private Map<String, String> template;

    public Map<String, String> getPod() {
        return pod;
    }

    public Map<String, String> getRc() {
        return rc;
    }

    public Map<String, String> getService() {
        return service;
    }

    public Map<String, String> getTemplate() {
        return template;
    }
}
