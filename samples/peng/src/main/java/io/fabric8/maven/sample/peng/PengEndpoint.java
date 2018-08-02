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

package io.fabric8.maven.sample.peng;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Path("/peng/{id}")
public class PengEndpoint {

    private Logger log = LoggerFactory.getLogger("peng");

    private static String pengId = UUID.randomUUID().toString().substring(0, 8);

    // ==================================================================================
    // Configuration

    @Value("${STRENGTH:2}")
    private int strength;

    // ====================================================================================

    @GET
    @Produces("text/plain")
    public String peng(@PathParam("id") String id) {
        Stroke stroke = Stroke.play(strength);
        return pengId + " " + stroke.toString();
    }
}
