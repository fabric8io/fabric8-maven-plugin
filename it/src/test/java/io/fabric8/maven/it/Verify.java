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

package io.fabric8.maven.it;/*
 *
 * Copyright 2015-2016 Red Hat, Inc.
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

import java.io.*;
import java.nio.charset.Charset;

import com.consol.citrus.Citrus;
import com.consol.citrus.context.TestContext;
import com.consol.citrus.message.DefaultMessage;
import com.consol.citrus.message.Message;
import com.consol.citrus.util.FileUtils;
import com.consol.citrus.validation.json.JsonMessageValidationContext;
import com.consol.citrus.validation.json.JsonTextMessageValidator;
import com.consol.citrus.validation.matcher.ValidationMatcherConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.util.FileCopyUtils;

/**
 * @author roland
 * @since 09/12/16
 */
public class Verify {

    public static void verifyResourceDescriptors(File actualPath, File expectedPath) throws IOException {
        String actualText = readFile(actualPath);
        String expectedText = readFile(expectedPath);


        JsonTextMessageValidator validator = new JsonTextMessageValidator();
        validator.setStrict(false);

        validator.validateMessagePayload(newMessage(actualText),
                                         newMessage(expectedText),
                                         new JsonMessageValidationContext(),
                                         createTestContext());

    }

    private static String readFile(File path) throws IOException {
        return new String(FileCopyUtils.copyToByteArray(new FileInputStream(path)), Charset.defaultCharset());
    }


    public static TestContext createTestContext() {
        // TODO: Doesnt work, getting classpath lookup issues
        // TestContext ctx = Citrus.newInstance().createTestContext();
        TestContext context = new TestContext();
        context.getValidationMatcherRegistry()
               .getValidationMatcherLibraries()
               .add(new ValidationMatcherConfig().getValidationMatcherLibrary());
        return context;
    }

    public static Message newMessage(String txt) throws IOException {
        return new DefaultMessage(asJson(txt));
    }

    public static String asJson(String txt) throws IOException {
        Object obj = new ObjectMapper(new YAMLFactory()).readValue(txt, Object.class);
        return new ObjectMapper().writeValueAsString(obj);
    }
}