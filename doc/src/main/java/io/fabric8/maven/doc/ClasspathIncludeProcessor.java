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
package io.fabric8.maven.doc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.asciidoctor.ast.DocumentRuby;
import org.asciidoctor.extension.IncludeProcessor;
import org.asciidoctor.extension.PreprocessorReader;

/**
 * @author roland
 * @since 11/07/16
 */
public class ClasspathIncludeProcessor extends IncludeProcessor {

    @Override
    public boolean handles(String target) {
        return target.startsWith("classpath:");
    }

    @Override
    public void process(DocumentRuby document,
                        PreprocessorReader reader,
                        String target,
                        Map<String, Object> attributes) {
        List<String> content = readContent(target);
        for (int i = content.size() - 1; i >= 0; i--) {
            String line = content.get(i);
            // See also https://github.com/asciidoctor/asciidoctorj/issues/437#issuecomment-192669617
            // Seems to be a hack to avoid mangling of paragraphes
            if (line.trim().equals("")) {
                line = " ";
            }
            reader.push_include(line, target, target, 1, attributes);
        }
    }

    private List<String> readContent(String target) {
        String resourcePath = target.substring("classpath:".length());
        InputStream is = getClass().getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IllegalArgumentException("No resource " + target + " could be found in the classpath");
        }

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));
        String line;
        List<String> lines = new ArrayList<>();
        try {
            while ((line = bufferedReader.readLine()) != null) {
                lines.add(line);
            }
            bufferedReader.close();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        return lines;
    }
}
