package io.fabric8.maven.plugin;
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

import java.util.HashMap;
import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * Intermediate class to allow flexible nested map configuration
 * @author roland
 * @since 28/07/16
 */
public class MapHandler {

    private Map<String, String> map = new HashMap<>();

    public Map<String, String> getMap() {
        return map;
    }
}
