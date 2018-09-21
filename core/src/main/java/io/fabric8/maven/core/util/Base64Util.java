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

import java.nio.charset.StandardCharsets;

import javax.xml.bind.DatatypeConverter;

/**
 * For java 7 or lower version, java.util doesn't provide a base64 encode/decode way
 */
public class Base64Util {

    public static String encodeToString(byte[] bytes) {
        return DatatypeConverter.printBase64Binary(bytes);
    }

    public static byte[] encode(byte[] bytes) {
        return encodeToString(bytes).getBytes(StandardCharsets.UTF_8);
    }

    public static String encodeToString(String raw) {
        return encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] encode(String raw) {
        return encode(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] decode(String raw) {
        return DatatypeConverter.parseBase64Binary(raw);
    }

    public static byte[] decode(byte[] bytes) {
        return decode(new String(bytes));
    }

    public static String decodeToString(String raw) {
        return new String(decode(raw));
    }

    public static String decodeToString(byte[] bytes) {
        return new String(decode(bytes));
    }
}
