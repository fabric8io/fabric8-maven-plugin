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
package io.fabric8.maven.core.util;

import org.apache.commons.lang3.StringUtils;

/**
 */
public class VersionUtil {
    /**
     * Compares two version strings such that "1.10.1" > "1.4" etc
     */
    public static int compareVersions(String v1, String v2) {
        String[] components1 = split(v1);
        String[] components2 = split(v2);
        int diff;
        int length = Math.min(components1.length, components2.length);
        for (int i = 0; i < length; i++) {
            String s1 = components1[i];
            String s2 = components2[i];
            Integer i1 = tryParseInteger(s1);
            Integer i2 = tryParseInteger(s2);
            if (i1 != null && i2 != null) {
                diff = i1.compareTo(i2);
            } else {
                // lets assume strings instead
                diff = s1.compareTo(s2);
            }
            if (diff != 0) {
                return diff;
            }
        }
        diff = Integer.compare(components1.length, components2.length);
        if (diff == 0) {
            if (v1 == v2) {
                return 0;
            }
            /* if v1 == null then v2 can't be null here (see 'if' above).
               So for v1 == null its always smaller than v2 */;
            return v1 != null ? v1.compareTo(v2) : -1;
        }
        return diff;
    }

    private static String[] split(String text) {
        if (text != null) {
            return text.split("[\\.-]");
        } else {
            return new String[0];
        }
    }

    private static Integer tryParseInteger(String text) {
        if (StringUtils.isNotBlank(text)) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return null;
    }
}
