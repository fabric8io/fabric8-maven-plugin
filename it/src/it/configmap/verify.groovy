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
import io.fabric8.maven.it.Verify

import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull;

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


[ "kubernetes", "openshift"  ].each {
  Verify.verifyResourceDescriptors(
          new File(basedir, sprintf("/target/classes/META-INF/fabric8/%s.yml",it)),
          new File(basedir, sprintf("/expected/%s.yml",it)))
}

Map selector = Verify.readWithPath(
        new File(basedir,"/target/classes/META-INF/fabric8/kubernetes/fabric8-maven-sample-config-map-deployment.yml"),
        "spec.selector.matchLabels")

assertNotNull(selector)
assertNull(selector.get("version"))

true
