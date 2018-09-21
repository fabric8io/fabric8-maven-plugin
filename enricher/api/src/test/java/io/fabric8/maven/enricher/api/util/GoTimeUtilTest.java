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
package io.fabric8.maven.enricher.api.util;

import org.junit.Test;

import static io.fabric8.maven.enricher.api.util.GoTimeUtil.durationSeconds;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GoTimeUtilTest {

    @Test
    public void testConversion() {
        assertEquals(new Integer(23), durationSeconds("23s"));
        assertEquals(new Integer(0), durationSeconds("0.5s"));
        assertEquals(new Integer(0), durationSeconds("3ms"));
        assertEquals(new Integer(0), durationSeconds("3ns"));
        assertEquals(new Integer(1), durationSeconds("1002ms"));
        assertEquals(new Integer(123), durationSeconds("2m3s"));
        assertEquals(new Integer(3663), durationSeconds("1h1m3s"));
        assertEquals(new Integer(1810), durationSeconds("0.5h0.1m4s"));
        assertEquals(new Integer(-15), durationSeconds("-15s"));
        assertEquals(new Integer(30), durationSeconds("2h-119.5m"));
    }

    @Test
    public void testEmpty() {
        assertNull(durationSeconds(null));
        assertNull(durationSeconds(""));
        assertNull(durationSeconds(" "));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testErrorOverflow() {
        durationSeconds(Integer.MAX_VALUE + "0s");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testErrorNoUnit() {
        durationSeconds("145");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testErrorUnknownUnit() {
        durationSeconds("1w");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testErrorUnparsable() {
        durationSeconds("ms");
    }



}
