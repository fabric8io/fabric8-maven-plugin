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
package io.k8spatterns.demo.random;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.health.Health;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;

@Health
@ApplicationScoped
public class RuntimeCheck implements HealthCheck {
    @Override
    public HealthCheckResponse call() {
        Runtime r = Runtime.getRuntime();
        return HealthCheckResponse.named("runtime")
                                  .withData("usedMemory", format(r.totalMemory() - r.freeMemory()))
                                  .withData("totalMemory", format(r.totalMemory()))
                                  .withData("maxMemory", format(r.maxMemory()))
                                  .withData("freeMemory", format(r.freeMemory()))
                                  .withData("availableProcessors", r.availableProcessors())
                                  .up()
                                  .build();
    }

     public static String format(long v) {
        if (v < 1024) return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format("%.1f %sB", (double)v / (1L << (z*10)), " KMGTPE".charAt(z));
    }
}
