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

package io.fabric8.maven.rt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.io.FileUtils;
import java.io.File;
import java.io.IOException;
import java.net.URL;

public class ReadYaml {

    public BoosterYaml readYaml(String boosterUrl) throws IOException {

        //Lets convert the string boosterUrl to URl format.
        URL url = new URL(boosterUrl);

        //Create a temp file to read the data from URL
        File file = File.createTempFile("booster", ".yaml");

        //Read the data from URl and copy it to File
        FileUtils.copyURLToFile(url, file);

        //Lets convert the file Bosster Yaml object and return BoosterYaml Object
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(file, BoosterYaml.class);
    }
}

