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

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import static io.fabric8.maven.core.util.FileUtil.getAbsolutePath;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author roland
 * @since 01/08/16
 */

public class ClassUtilTest {

    @Test
    public void findOne() throws IOException {
        File root = getRelativePackagePath("mainclass/one");
        List<String> ret = ClassUtil.findMainClasses(root);
        assertEquals(1,ret.size());
        assertEquals("sub.OneMain", ret.get(0));
    }

    @Test
    public void findTwo() throws IOException {
        File root = getRelativePackagePath("mainclass/two");
        Set<String> ret = new HashSet<>(ClassUtil.findMainClasses(root));
        assertEquals(2,ret.size());
        assertTrue(ret.contains("OneMain"));
        assertTrue(ret.contains("another.sub.a.bit.deeper.TwoMain"));
    }

    @Test
    public void findNone() throws IOException {
        File root = getRelativePackagePath("mainclass/zero");
        List<String> ret = ClassUtil.findMainClasses(root);
        assertEquals(0,ret.size());
    }

    private File getRelativePackagePath(String subpath) {
    	File parent =        		
            new File(getAbsolutePath(this.getClass().getProtectionDomain().getCodeSource().getLocation()));
        String intermediatePath = getClass().getPackage().getName().replace(".","/");
        return new File(new File(parent, intermediatePath),subpath);
    }
}
